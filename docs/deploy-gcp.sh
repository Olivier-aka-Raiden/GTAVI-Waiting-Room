# ============================================================
# GTA VI Waiting Room — GCP Deployment Guide
# ============================================================
# Prerequisites:
#   gcloud CLI installed and authenticated
#   gcloud config set project YOUR_PROJECT_ID
#   Enable APIs: Cloud Run, Cloud Scheduler, Artifact Registry, Secret Manager

# -------------------------------------------------------
# 1. CONFIGURATION — set these once
# -------------------------------------------------------
export GCP_PROJECT="gtavi-waiting-room-502319"           # your GCP project ID
export GCP_REGION="europe-west1"                   # closest to you (CH)
export SERVICE_NAME="gtavi-api"                    # Cloud Run service name
export JOB_NAME="gtavi-monitor"                    # Cloud Scheduler job name
export ARTIFACT_REPO="gtavi"                       # Artifact Registry repo

# -------------------------------------------------------
# 2. STORE SECRETS in Secret Manager
# -------------------------------------------------------
# Never pass secrets via --set-env-vars. Use Secret Manager.
echo "sk-your-deepseek-key" | gcloud secrets create DEEPSEEK_API_KEY \
  --data-file=- --project=$GCP_PROJECT

echo "neo4j+s://xxxxxxxx.databases.neo4j.io" | gcloud secrets create NEO4J_URI \
  --data-file=- --project=$GCP_PROJECT

echo "neo4j" | gcloud secrets create NEO4J_USERNAME \
  --data-file=- --project=$GCP_PROJECT

echo "your-aura-db-password" | gcloud secrets create NEO4J_PASSWORD \
  --data-file=- --project=$GCP_PROJECT

# Generate and store the shared secret for /internal/* endpoints
openssl rand -hex 32 | gcloud secrets create INTERNAL_SHARED_SECRET \
  --data-file=- --project=$GCP_PROJECT

# Firebase (optional — only if push notifications are enabled)
gcloud secrets create FIREBASE_SERVICE_ACCOUNT_JSON \
  --data-file=./firebase-service-account.json --project=$GCP_PROJECT

# -------------------------------------------------------
# 3. BUILD & PUSH the container
# -------------------------------------------------------
# Create Artifact Registry repo (one-time)
gcloud artifacts repositories create $ARTIFACT_REPO \
  --repository-format=docker \
  --location=$GCP_REGION \
  --project=$GCP_PROJECT

# Build via Cloud Build (no local Docker needed)
cd backend
gcloud builds submit \
  --tag=$GCP_REGION-docker.pkg.dev/$GCP_PROJECT/$ARTIFACT_REPO/$SERVICE_NAME:latest \
  --project=$GCP_PROJECT

# -------------------------------------------------------
# 4. DEPLOY the BFF (Backend For Frontend) on Cloud Run
# -------------------------------------------------------
gcloud run deploy $SERVICE_NAME \
  --image=$GCP_REGION-docker.pkg.dev/$GCP_PROJECT/$ARTIFACT_REPO/$SERVICE_NAME:latest \
  --region=$GCP_REGION \
  --project=$GCP_PROJECT \
  --platform=managed \
  --allow-unauthenticated \
  --memory=1Gi \
  --cpu=1 \
  --min-instances=0 \
  --max-instances=3 \
  --concurrency=80 \
  --timeout=60s \
  --set-env-vars="QUARKUS_PROFILE=prod" \
  --set-env-vars="MONITORING_ENABLED=true" \
  --set-env-vars="FCM_ENABLED=false" \
  --set-env-vars="DEEPSEEK_BASE_URL=https://api.deepseek.com" \
  --set-env-vars="DEEPSEEK_MODEL=deepseek-v4-pro" \
  --set-secrets="DEEPSEEK_API_KEY=DEEPSEEK_API_KEY:latest" \
  --set-secrets="NEO4J_URI=NEO4J_URI:latest" \
  --set-secrets="NEO4J_USERNAME=NEO4J_USERNAME:latest" \
  --set-secrets="NEO4J_PASSWORD=NEO4J_PASSWORD:latest" \
  --set-secrets="INTERNAL_SHARED_SECRET=INTERNAL_SHARED_SECRET:latest"

# Grab the deployed URL
export BFF_URL=$(gcloud run services describe $SERVICE_NAME \
  --region=$GCP_REGION --project=$GCP_PROJECT \
  --format="value(status.url)")

echo "BFF deployed at: $BFF_URL"

# Quick smoke test
curl -s "$BFF_URL/q/health" | jq .

# -------------------------------------------------------
# 5. CLOUD SCHEDULER — periodic monitoring trigger
# -------------------------------------------------------
# Every 10 minutes, Cloud Scheduler POSTs to the /internal/jobs/check-updates
# endpoint with the shared secret header.
export SHARED_SECRET=$(gcloud secrets versions access latest \
  --secret=INTERNAL_SHARED_SECRET --project=$GCP_PROJECT)

gcloud scheduler jobs create http $JOB_NAME \
  --location=$GCP_REGION \
  --project=$GCP_PROJECT \
  --schedule="*/10 * * * *" \
  --uri="$BFF_URL/internal/jobs/check-updates" \
  --http-method=POST \
  --headers="X-Internal-Secret=$SHARED_SECRET,Content-Type=application/json" \
  --attempt-deadline=180s \
  --time-zone="Europe/Zurich" \
  --description="Triggers GTA VI source monitoring (Rockstar, retailers, Amazon)"

# -------------------------------------------------------
# 6. (OPTIONAL) CORS — update for the Vercel frontend
# -------------------------------------------------------
# If your frontend is on Vercel, update the CORS origin:
gcloud run services update $SERVICE_NAME \
  --region=$GCP_REGION --project=$GCP_PROJECT \
  --set-env-vars="QUARKUS_HTTP_CORS_ORIGINS=https://gta-vi-waiting-room.vercel.app"

# -------------------------------------------------------
# VERIFY EVERYTHING
# -------------------------------------------------------
echo "=== BFF health check ==="
curl -s "$BFF_URL/q/health" | jq .

echo "=== GTA VI data ==="
curl -s "$BFF_URL/api/v1/games/gta-vi" | jq .name,.releaseDate

echo "=== Scheduler job ==="
gcloud scheduler jobs describe $JOB_NAME \
  --location=$GCP_REGION --project=$GCP_PROJECT

# Force a monitoring run to test the pipeline end-to-end:
curl -s -X POST "$BFF_URL/internal/jobs/check-updates" \
  -H "X-Internal-Secret: $SHARED_SECRET" \
  -H "Content-Type: application/json" | jq .
