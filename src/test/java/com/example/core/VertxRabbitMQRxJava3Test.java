package com.example.core;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rabbitmq.RabbitMQOptions;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.rabbitmq.RabbitMQClient;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testcontainers.containers.RabbitMQContainer;

@ExtendWith(VertxExtension.class)
public class VertxRabbitMQRxJava3Test {

  static final RabbitMQContainer rabbitmq = new RabbitMQContainer(
    "rabbitmq:3.11-management-alpine"
  )
    .withUser("user", "password", Set.of("administrator"))
    .withPermission("/", "user", ".*", ".*", ".*");
  static RabbitMQClient client;

  @BeforeAll
  static void beforeAll(Vertx vertx) {
    rabbitmq.start();

    RabbitMQOptions config = new RabbitMQOptions();
    config.setHost(rabbitmq.getHost());
    config.setPort(rabbitmq.getAmqpPort());
    config.setUser("user");
    config.setPassword("password");
    client = RabbitMQClient.create(vertx, config);

    System.out.println(rabbitmq.getHttpUrl());
  }

  @AfterAll
  static void afterAll() {
    client.stop().subscribe();
    rabbitmq.stop();
  }

  @Test
  void should_consume_message_from_queue(VertxTestContext testContext) {
    var producerPublished = testContext.checkpoint();
    var consumerConsumed = testContext.checkpoint();

    client
      .rxStart()
      .doOnComplete(() -> System.out.println("Started"))
      .doOnError(th -> System.err.println("Not started: " + th.getMessage()))
      // Create exchange, queue and binding
      .andThen(
        Completable.defer(() ->
          client
            .exchangeDeclare("my-exchange", "topic", true, false)
            .doOnComplete(() -> System.out.println("Exchange declared"))
            .doOnError(th ->
              System.err.println("Exchange not created: " + th.getMessage())
            )
            .andThen(
              Completable.defer(() ->
                client
                  .queueDeclare("my-queue", true, false, false)
                  .doOnSuccess(q -> System.out.println("Queue declared"))
                  .flatMapCompletable(queue ->
                    client
                      .queueBind(
                        queue.getQueue(),
                        "my-exchange",
                        "my.routing.key"
                      )
                      .doOnComplete(() -> System.out.println("Binding created"))
                  )
              )
            )
        )
      )
      // Publish a message in the exchange
      .andThen(
        Completable.defer(() ->
          client
            .basicPublish(
              "my-exchange",
              "my.routing.key",
              Buffer.buffer("Hello RabbitMQ from Vert.x!")
            )
            .doOnComplete(producerPublished::flag)
            .doOnError(t ->
              testContext.failNow(new Exception("Failed to publish message", t))
            )
        )
      )
      // Consume message from the queue
      .andThen(
        Completable.defer(() ->
          client
            .basicConsumer("my-queue")
            .doOnSuccess(c -> {
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
            .doOnError(t ->
              testContext.failNow(new Exception("Failed to consume message", t))
            )
            .ignoreElement()
        )
      )
      .subscribe(
        () -> System.out.println("Completed"),
        (@NonNull Throwable e) -> System.err.println(e.getMessage())
      );
  }
}
