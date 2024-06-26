package kolbasa.cluster.simple

import kolbasa.producer.datasource.Producer
import kolbasa.queue.Queue

interface ProducerProvider {

    fun <Data, Meta : Any> producer(queue: Queue<Data, Meta>): Producer<Data, Meta>

    fun <Data, Meta : Any> producer(queue: Queue<Data, Meta>, shard: Int): Producer<Data, Meta>

}
