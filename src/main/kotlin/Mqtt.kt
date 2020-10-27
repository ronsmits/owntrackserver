import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.eclipse.paho.client.mqttv3.persist.MqttDefaultFilePersistence
import org.slf4j.LoggerFactory
import java.util.*
import javax.json.JsonObject

object Mqtt {
    lateinit var broker: String
    val logger = LoggerFactory.getLogger(Mqtt::class.java)

    val client by lazy {
        logger.info("setting up client")
        MqttClient(broker, UUID.randomUUID().toString(), MemoryPersistence())
    }

    fun publish(topic: String, message: JsonObject) = publish(topic, message.toString())

    fun publish(topic: String, message: String) = client.publish(topic, MqttMessage(message.toByteArray()))

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
