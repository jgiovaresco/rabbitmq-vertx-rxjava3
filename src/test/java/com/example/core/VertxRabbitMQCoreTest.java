package com.example.core;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rabbitmq.RabbitMQOptions;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.RabbitMQContainer;

@ExtendWith(VertxExtension.class)
public class VertxRabbitMQCoreTest {

  static final RabbitMQContainer rabbitmq = new RabbitMQContainer(
    "rabbitmq:3.11-management-alpine"
  )
    .withUser("user", "password", Set.of("administrator"))
    .withPermission("/", "user", ".*", ".*", ".*");
  static io.vertx.rabbitmq.RabbitMQClient client;

  @BeforeAll
  static void beforeAll(Vertx vertx) {
    rabbitmq.start();

    RabbitMQOptions config = new RabbitMQOptions();
    config.setHost(rabbitmq.getHost());
    config.setPort(rabbitmq.getAmqpPort());
    config.setUser("user");
    config.setPassword("password");
    client = io.vertx.rabbitmq.RabbitMQClient.create(vertx, config);
  }

  @AfterAll
  static void afterAll() {
    client.stop();
    rabbitmq.stop();
  }

  @Test
  void should_consume_message_from_queue(VertxTestContext testContext)
    throws InterruptedException {
    var producerPublished = testContext.checkpoint();
    var consumerConsumed = testContext.checkpoint();

    client
      .start()
      // Create exchange, queue and binding
      .compose(start ->
        client
          .exchangeDeclare("my-exchange", "topic", true, false)
          .compose(exchange ->
            client.queueDeclare("my-queue", true, false, false)
          )
          .flatMap(queue ->
            client.queueBind(queue.getQueue(), "my-exchange", "my.routing.key")
          )
      )
      // Publish a message in the exchange
      .compose(e ->
        client
          .basicPublish(
            "my-exchange",
            "my.routing.key",
            Buffer.buffer("Hello RabbitMQ from Vert.x!")
          )
          .onSuccess(s -> producerPublished.flag())
          .onFailure(t ->
            testContext.failNow(new Exception("Failed to publish message", t))
          )
      )
      // Consume message from the queue
      .compose(e ->
        client
          .basicConsumer("my-queue")
          .onSuccess(c -> {
            c.handler(message -> {
              testContext.verify(() ->
                Assertions
                  .assertThat(message.body().toString())
                  .isEqualTo("Hello RabbitMQ from Vert.x!")
              );
              consumerConsumed.flag();
            });
            c.endHandler(end -> testContext.completeNow());
            c.exceptionHandler(t ->
              testContext.failNow(
                new Exception("Exception while consuming message", t)
              )
            );
          })
          .onFailure(t ->
            testContext.failNow(new Exception("Failed to consume message", t))
          )
      );
  }
}
