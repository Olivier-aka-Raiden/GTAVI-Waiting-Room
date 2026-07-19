package com.gtavi.nativeimage;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.apache.logging.log4j.spi.ExtendedLogger;
import org.apache.logging.log4j.spi.ExtendedLoggerWrapper;

/**
 * Registers gRPC shaded Netty, Log4j v1/v2, Firebase, and Google Auth
 * classes for native image reflection.
 */
@RegisterForReflection(
    targets = {
        // gRPC shaded Netty logging — all four factories
        io.grpc.netty.shaded.io.netty.util.internal.logging.InternalLoggerFactory.class,
        io.grpc.netty.shaded.io.netty.util.internal.logging.JdkLoggerFactory.class,

        // gRPC shaded Netty internal
        io.grpc.netty.shaded.io.netty.util.internal.PlatformDependent.class,
        io.grpc.netty.shaded.io.netty.util.internal.shaded.org.jctools.queues.MpscArrayQueue.class,

        // Log4j2 SPI
        ExtendedLogger.class,
        ExtendedLoggerWrapper.class,

        // Log4j v1
        org.apache.log4j.Logger.class,
        org.apache.log4j.Category.class,

        // gRPC service provider
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
        "io.grpc.netty.shaded.io.netty.util.internal.logging.Log4J2LoggerFactory",
        "io.grpc.netty.shaded.io.netty.util.internal.logging.Log4JLogger",
        "io.grpc.netty.shaded.io.netty.util.internal.logging.Log4JLoggerFactory",
        "io.grpc.netty.shaded.io.netty.util.internal.logging.Slf4JLogger",
        "io.grpc.netty.shaded.io.netty.util.internal.logging.Slf4JLoggerFactory",
        "io.grpc.netty.shaded.io.netty.util.internal.PlatformDependent0",
        "io.grpc.netty.shaded.io.netty.util.internal.CleanerJava9",

        // Google gax native-image utils warnings
        "io.grpc.netty.shaded.io.netty.channel.ProtocolNegotiators",
        "com.google.common.util.concurrent.AbstractFuture$Waiter",
    }
)
public class QuarkusNativeReflectionFix {
}
