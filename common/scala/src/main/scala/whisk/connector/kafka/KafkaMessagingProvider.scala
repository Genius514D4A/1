package whisk.connector.kafka

import akka.actor.ActorSystem
import scaldi.Injector
import whisk.common.Logging
import whisk.core.WhiskConfig
import whisk.core.connector.MessageConsumer
import whisk.core.connector.MessageProducer
import whisk.core.connector.MessagingProvider
import whisk.spi.SpiFactoryModule
/**
  * Created by tnorris on 6/20/17.
  */
class KafkaMessagingProvider(actorSystem:ActorSystem, config:WhiskConfig)(implicit logging:Logging) extends MessagingProvider {
  def getConsumer(groupId:String, topic: String, maxdepth:Int): MessageConsumer = {
    new KafkaConsumerConnector(config.kafkaHost, groupId, topic, maxdepth)(logging)
  }
  def getProducer(): MessageProducer = new KafkaProducerConnector(config.kafkaHost, actorSystem.dispatcher)
}

class KafkaMessagingProviderModule extends SpiFactoryModule[MessagingProvider]{
  def getInstance(implicit injector:Injector):MessagingProvider = {
    val actorSystem = inject[ActorSystem]
    val config = inject[WhiskConfig]
    implicit val logging = inject[Logging]
    new KafkaMessagingProvider(actorSystem, config)
  }
}