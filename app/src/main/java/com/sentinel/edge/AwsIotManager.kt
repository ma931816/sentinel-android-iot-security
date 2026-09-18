package com.sentinel.edge

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import software.amazon.awssdk.crt.CRT
import software.amazon.awssdk.crt.mqtt5.Mqtt5Client
import software.amazon.awssdk.crt.mqtt5.Mqtt5ClientOptions
import software.amazon.awssdk.crt.mqtt5.OnAttemptingConnectReturn
import software.amazon.awssdk.crt.mqtt5.OnConnectionFailureReturn
import software.amazon.awssdk.crt.mqtt5.OnConnectionSuccessReturn
import software.amazon.awssdk.crt.mqtt5.OnDisconnectionReturn
import software.amazon.awssdk.crt.mqtt5.OnStoppedReturn
import software.amazon.awssdk.iot.AwsIotMqtt5ClientBuilder
import software.amazon.awssdk.crt.mqtt5.packets.PublishPacket
import software.amazon.awssdk.crt.mqtt5.QOS

class AwsIotManager(
    private val context: Context
) {

    companion object {

        private const val DEVICE_ID = "SENTINEL-001"

        /*
         * AWS IoT Device Data Endpoint
         */
        private const val AWS_IOT_ENDPOINT =
            "a1hwvjs89dquf9-ats.iot.us-east-1.amazonaws.com"

        private const val CERTIFICATE_FILE =
            "sentinel-certificate.pem.crt"

        private const val PRIVATE_KEY_FILE =
            "sentinel-private.pem.key"

        private const val ROOT_CA_FILE =
            "AmazonRootCA1.pem"

        /*
         * Normal Sentinel telemetry topic
         */
        private const val TELEMETRY_TOPIC =
            "sentinel/SENTINEL-001/telemetry"

        /*
         * AWS IoT Classic Device Shadow update topic
         */
        private const val SHADOW_UPDATE_TOPIC =
            "\$aws/things/SENTINEL-001/shadow/update"
    }

    private var mqttClient: Mqtt5Client? = null

    private val scope =
        CoroutineScope(Dispatchers.IO)

    fun connect() {

        scope.launch {

            try {

                val certificatePath =
                    copyAssetToInternalStorage(
                        "certs/$CERTIFICATE_FILE"
                    )

                val privateKeyPath =
                    copyAssetToInternalStorage(
                        "certs/$PRIVATE_KEY_FILE"
                    )

                val rootCaPath =
                    copyAssetToInternalStorage(
                        "certs/$ROOT_CA_FILE"
                    )

                val lifecycleEvents =
                    object : Mqtt5ClientOptions.LifecycleEvents {

                        override fun onAttemptingConnect(
                            client: Mqtt5Client,
                            onAttemptingConnectReturn:
                            OnAttemptingConnectReturn
                        ) {

                            MainActivity.awsStatusText =
                                "AWS IoT: CONNECTING"
                        }

                        override fun onConnectionSuccess(
                            client: Mqtt5Client,
                            onConnectionSuccessReturn:
                            OnConnectionSuccessReturn
                        ) {

                            MainActivity.awsStatusText =
                                "AWS IoT: CONNECTED"
                        }

                        override fun onConnectionFailure(
                            client: Mqtt5Client,
                            onConnectionFailureReturn:
                            OnConnectionFailureReturn
                        ) {

                            val error =
                                CRT.awsErrorString(
                                    onConnectionFailureReturn.errorCode
                                )

                            MainActivity.awsStatusText =
                                "AWS IoT: CONNECTION FAILED\n$error"
                        }

                        override fun onDisconnection(
                            client: Mqtt5Client,
                            onDisconnectionReturn:
                            OnDisconnectionReturn
                        ) {

                            MainActivity.awsStatusText =
                                "AWS IoT: DISCONNECTED"
                        }

                        override fun onStopped(
                            client: Mqtt5Client,
                            onStoppedReturn:
                            OnStoppedReturn
                        ) {

                            MainActivity.awsStatusText =
                                "AWS IoT: STOPPED"
                        }
                    }

                val builder =
                    AwsIotMqtt5ClientBuilder
                        .newDirectMqttBuilderWithMtlsFromPath(
                            AWS_IOT_ENDPOINT,
                            certificatePath,
                            privateKeyPath
                        )

                builder
                    .withClientId(DEVICE_ID)
                    .withCertificateAuthorityFromPath(
                        null,
                        rootCaPath
                    )
                    .withLifeCycleEvents(
                        lifecycleEvents
                    )

                mqttClient =
                    builder.build()

                mqttClient?.start()

            } catch (e: Exception) {

                MainActivity.awsStatusText =
                    "AWS IoT: ERROR\n${e.message}"
            }
        }
    }

    /*
     * Publish normal Sentinel telemetry
     */
    fun publishTelemetry(payload: String) {

        val client = mqttClient

        if (client == null) {

            MainActivity.awsStatusText =
                "AWS IoT: NOT CONNECTED"

            return
        }

        try {

            val publishPacket =
                PublishPacket.PublishPacketBuilder()
                    .withTopic(TELEMETRY_TOPIC)
                    .withPayload(
                        payload.toByteArray()
                    )
                    .withQOS(
                        QOS.AT_MOST_ONCE
                    )
                    .build()

            client.publish(
                publishPacket
            )

            MainActivity.awsStatusText =
                "AWS IoT: TELEMETRY SENT"

        } catch (e: Exception) {

            MainActivity.awsStatusText =
                "AWS IoT: PUBLISH ERROR\n${e.message}"
        }
    }

    /*
     * Update AWS IoT Device Shadow
     *
     * The values are written into:
     *
     * state.reported
     */
    fun updateDeviceShadow(
        latitude: Double?,
        longitude: Double?,
        battery: Int,
        motion: String,
        networkStatus: String
    ) {

        val client = mqttClient

        if (client == null) {

            MainActivity.awsStatusText =
                "AWS IoT: NOT CONNECTED"

            return
        }

        try {

            val payload =
                """
                {
                  "state": {
                    "reported": {
                      "deviceId": "$DEVICE_ID",
                      "deviceStatus": "ONLINE",
                      "latitude": ${latitude ?: "null"},
                      "longitude": ${longitude ?: "null"},
                      "battery": $battery,
                      "motion": "$motion",
                      "networkStatus": "$networkStatus"
                    }
                  }
                }
                """.trimIndent()

            val publishPacket =
                PublishPacket.PublishPacketBuilder()
                    .withTopic(
                        SHADOW_UPDATE_TOPIC
                    )
                    .withPayload(
                        payload.toByteArray()
                    )
                    .withQOS(
                        QOS.AT_MOST_ONCE
                    )
                    .build()

            client.publish(
                publishPacket
            )

            MainActivity.awsStatusText =
                "AWS IoT: SHADOW UPDATED"

        } catch (e: Exception) {

            MainActivity.awsStatusText =
                "AWS IoT: SHADOW UPDATE ERROR\n${e.message}"
        }
    }

    fun disconnect() {

        try {

            mqttClient?.stop()

        } catch (_: Exception) {
        }

        mqttClient = null

        MainActivity.awsStatusText =
            "AWS IoT: DISCONNECTED"
    }

    private fun copyAssetToInternalStorage(
        assetPath: String
    ): String {

        val fileName =
            assetPath.substringAfterLast("/")

        val outputFile =
            context.getFileStreamPath(fileName)

        if (!outputFile.exists()) {

            try {

                context.assets.open(assetPath).use { input ->

                    outputFile.outputStream().use { output ->

                        input.copyTo(output)
                    }
                }

            } catch (e: Exception) {

                throw Exception(
                    "Could not open asset: $assetPath | ${e::class.java.simpleName} | ${e.message}"
                )
            }
        }

        return outputFile.absolutePath
    }
}
