package com.gtavi.nativeimage;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.ExtendedLoggerWrapper;

/**
 * Registers gRPC shaded Netty, Log4j2, Firebase, and Google Auth
 * classes for native image reflection.
 *
 * Public classes use class literals; package-private classes use
 * string classNames to bypass Java access checks at compile time.
 */
@RegisterForReflection(
    targets = {
        // gRPC shaded Netty logging (public API)
        io.grpc.netty.shaded.io.netty.util.internal.logging.Log4J2LoggerFactory.class,
        io.grpc.netty.shaded.io.netty.util.internal.logging.InternalLoggerFactory.class,
        io.grpc.netty.shaded.io.netty.util.internal.logging.JdkLoggerFactory.class,

        // gRPC shaded Netty internal (public API)
        io.grpc.netty.shaded.io.netty.util.internal.PlatformDependent.class,
        io.grpc.netty.shaded.io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueue.class,

        // Log4j2 SPI
        ExtendedLogger.class,
        ExtendedLoggerWrapper.class,

        // gRPC core service provider
        io.grpc.netty.shaded.io.grpc.netty.NettyChannelProvider.class,

        // Firebase + Google Auth
        com.google.auth.oauth2.GoogleCredentials.class,
        com.google.auth.oauth2.ServiceAccountCredentials.class,
        com.google.firebase.FirebaseApp.class,
        com.google.firebase.FirebaseOptions.class,
        com.google.firebase.messaging.FirebaseMessaging.class,
        com.google.firebase.messaging.Message.class,
        com.google.firebase.messaging.Notification.class,
    },
    classNames = {
        // Package-private gRPC shaded Netty internals
        "io.grpc.netty.shaded.io.netty.util.internal.logging.Log4J2Logger",
        "io.grpc.netty.shaded.io.netty.util.internal.PlatformDependent0",
        "io.grpc.netty.shaded.io.netty.util.internal.CleanerJava9",
    }
)
public class QuarkusNativeReflectionFix {
}
