package com.gtavi.nativeimage;

import com.gtavi.api.dto.*;
import com.gtavi.domain.*;
import com.gtavi.service.GameService;
import io.quarkus.runtime.annotations.RegisterForReflection;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.ExtendedLoggerWrapper;

/**
 * Registers all classes that GraalVM native image cannot discover automatically:
 * gRPC shaded Netty, Log4j, Firebase, Google Auth, API DTOs, and domain classes.
 */
@RegisterForReflection(
    targets = {
        // ── API DTOs (Jackson serialization) ──
        GameOverviewResponse.class,
        SystemStatusResponse.class,
        ReleaseInfoResponse.class,
        TrailerResponse.class,
        EditionResponse.class,
        RetailOfferResponse.class,
        ChangeEventResponse.class,

        // ── Domain classes ──
        Game.class,
        Trailer.class,
        Edition.class,
        Retailer.class,
        RetailOffer.class,
        ChangeEvent.class,

        // ── Service records ──
        GameService.MonitoringHealth.class,

        // ── gRPC shaded Netty logging ──
        io.grpc.netty.shaded.io.netty.util.internal.logging.InternalLoggerFactory.class,
        io.grpc.netty.shaded.io.netty.util.internal.logging.JdkLoggerFactory.class,

        // ── gRPC shaded Netty internal ──
        io.grpc.netty.shaded.io.netty.util.internal.PlatformDependent.class,
        io.grpc.netty.shaded.io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueue.class,

        // ── Log4j2 SPI ──
        ExtendedLogger.class,
        ExtendedLoggerWrapper.class,

        // ── Log4j v1 ──
        org.apache.log4j.Logger.class,
        org.apache.log4j.Category.class,

        // ── gRPC service provider ──
        io.grpc.netty.shaded.io.grpc.netty.NettyChannelProvider.class,

        // ── Firebase + Google Auth ──
        com.google.auth.oauth2.GoogleCredentials.class,
        com.google.auth.oauth2.ServiceAccountCredentials.class,
        com.google.firebase.FirebaseApp.class,
        com.google.firebase.FirebaseOptions.class,
        com.google.firebase.messaging.FirebaseMessaging.class,
        com.google.firebase.messaging.Message.class,
        com.google.firebase.messaging.Notification.class,
    },
    classNames = {
        "io.grpc.netty.shaded.io.netty.util.internal.logging.Log4J2Logger",
        "io.grpc.netty.shaded.io.netty.util.internal.logging.Log4J2LoggerFactory",
        "io.grpc.netty.shaded.io.netty.util.internal.logging.Log4JLogger",
        "io.grpc.netty.shaded.io.netty.util.internal.logging.Log4JLoggerFactory",
        "io.grpc.netty.shaded.io.netty.util.internal.logging.Slf4JLogger",
        "io.grpc.netty.shaded.io.netty.util.internal.logging.Slf4JLoggerFactory",
        "io.grpc.netty.shaded.io.netty.util.internal.PlatformDependent0",
        "io.grpc.netty.shaded.io.netty.util.internal.CleanerJava9",
        "io.grpc.netty.shaded.io.netty.channel.ProtocolNegotiators",
        "com.google.common.util.concurrent.AbstractFuture$Waiter",
    }
)
public class QuarkusNativeReflectionFix {
}
