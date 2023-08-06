package kolbasa.consumer

import kolbasa.Kolbasa
import kolbasa.consumer.filter.Condition
import kolbasa.pg.DatabaseExtensions.useStatement
import kolbasa.queue.Queue
import kolbasa.stats.GlobalStats
import kolbasa.stats.QueueStats
import kolbasa.utils.LongBox
import java.sql.Connection

class ConnectionAwareDatabaseConsumer<V, Meta : Any>(
    private val queue: Queue<V, Meta>,
    private val consumerOptions: ConsumerOptions = ConsumerOptions()
) : ConnectionAwareConsumer<V, Meta> {

    private val queueStats: QueueStats

    init {
        Kolbasa.registerQueue(queue)
        queueStats = GlobalStats.getStatsForQueue(queue)
    }

    override fun receive(connection: Connection): Message<V, Meta>? {
        return receive(connection, ReceiveOptions())
    }

    override fun receive(connection: Connection, filter: () -> Condition<Meta>): Message<V, Meta>? {
        return receive(connection, ReceiveOptions(filter = filter()))
    }

    override fun receive(connection: Connection, receiveOptions: ReceiveOptions<Meta>): Message<V, Meta>? {
        val result = receive(connection, limit = 1, receiveOptions)
        return result.firstOrNull()
    }

    override fun receive(connection: Connection, limit: Int): List<Message<V, Meta>> {
        return receive(connection, limit, ReceiveOptions())
    }

    override fun receive(connection: Connection, limit: Int, filter: () -> Condition<Meta>): List<Message<V, Meta>> {
        return receive(connection, limit, ReceiveOptions(filter = filter()))
    }

    override fun receive(connection: Connection, limit: Int, receiveOptions: ReceiveOptions<Meta>): List<Message<V, Meta>> {
        // delete expired messages before next read
        SweepHelper.sweep(connection, queue)

        // read
        val query = ConsumerSchemaHelpers.generateSelectPreparedQuery(queue, consumerOptions, receiveOptions, limit)
        return connection.prepareStatement(query).use { preparedStatement ->
            ConsumerSchemaHelpers.fillSelectPreparedQuery(queue, consumerOptions, receiveOptions, preparedStatement)
            preparedStatement.executeQuery().use { resultSet ->
                val approxBytesCounter = LongBox()
                val result = ArrayList<Message<V, Meta>>(limit)

                while (resultSet.next()) {
                    result += ConsumerSchemaHelpers.read(queue, receiveOptions, resultSet, approxBytesCounter)
                }

                queueStats.receiveInc(result.size.toLong(), approxBytesCounter.get())
                result
            }
        }
    }

    override fun delete(connection: Connection, messageId: Long): Int {
        val query = ConsumerSchemaHelpers.generateDeleteQuery(queue, messageId)
        return connection.useStatement { statement ->
            statement.executeUpdate(query)
        }
    }

    override fun delete(connection: Connection, messageIds: List<Long>): Int {
        if (messageIds.isEmpty()) {
            return 0
        }

        val query = ConsumerSchemaHelpers.generateDeleteQuery(queue, messageIds)
        return connection.useStatement { statement ->
            statement.executeUpdate(query)
        }
    }

    override fun delete(connection: Connection, message: Message<V, Meta>): Int {
        return delete(connection, message.id)
    }

    override fun delete(connection: Connection, messages: Collection<Message<V, Meta>>): Int {
        return delete(connection, messages.map(Message<V, Meta>::id))
    }
}
