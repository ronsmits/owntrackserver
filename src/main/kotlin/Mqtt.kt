import org.eclipse.paho.client.mqttv3.*
import org.slf4j.LoggerFactory

object Mqtt {
    lateinit var broker: String
    val logger = LoggerFactory.getLogger(Mqtt::class.java)

    val client by lazy {
        logger.info("setting up client")
        MqttClient(broker, "owntrack-server")
    }

    fun connect(broker: String) {
        this.broker = broker
        try {
            client.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(p0: Boolean, p1: String?) {
                    logger.info("connected!")
                }

                override fun messageArrived(p0: String?, p1: MqttMessage?) {
                    logger.info("message arrived: p0 $p0 - p1 - $p1")
                }

                override fun connectionLost(p0: Throwable?) {
                    logger.info("connection lost ${p0?.message}")
                }

                override fun deliveryComplete(p0: IMqttDeliveryToken?) {
                    logger.debug("message delivered")
                }

            })
            client.connect(MqttConnectOptions().apply {
                isCleanSession = true
                isAutomaticReconnect = true
                connectionTimeout = 10
            })
        } catch (e: Exception) {
            logger.info("something went wrong", e)
        }
    }

}
