/*
 * Copyright © 2022-2023 by Pervasive Cats S.r.l.s.
 *
 * All Rights Reserved.
 */

package io.github.pervasivecats
package demo

import java.nio.ByteBuffer
import java.util.concurrent.CompletionException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.DurationInt
import scala.io.StdIn
import scala.jdk.OptionConverters.*
import scala.util.Failure
import scala.util.Success

import akka.actor.ActorSystem as ClassicActorSystem
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.client.RequestBuilding.Delete
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.client.RequestBuilding.Post
import akka.http.scaladsl.client.RequestBuilding.Put
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.unmarshalling.Unmarshal
import org.eclipse.ditto.base.model.common.HttpStatus
import org.eclipse.ditto.client.DittoClient
import org.eclipse.ditto.json.JsonObject
import org.eclipse.ditto.messages.model.Message
import org.eclipse.ditto.things.model.Thing
import org.eclipse.ditto.things.model.ThingId
import org.eclipse.ditto.things.model.signals.commands.exceptions.ThingNotAccessibleException
import spray.json.JsObject
import spray.json.enrichAny

import demo.AnyOps.*
import demo.Entity.*
import demo.Requests.*
import demo.Responses.*

@SuppressWarnings(Array("org.wartremover.warts.ToString", "org.wartremover.warts.Throw", "scalafix:DisableSyntax.throw"))
object Demo extends SprayJsonSupport {

  private val cartUri = "http://localhost:8084/cart"
  private val itemCategoryUri = "http://localhost:8083/item_category"
  private val catalogItemUri = "http://localhost:8083/catalog_item"
  private val itemUri = "http://localhost:8083/item"
  private val customerUri = "http://localhost:8082/customer"

  private val responsePrefix = ">> [RESPONSE] "

  private def initializeStore(
    httpClient: HttpExt,
    dittoClient: DittoClient,
    store: Long
  )(
    using
    ExecutionContext,
    ClassicActorSystem
  ): (Long, Long, Long, Long, Long) = {
    // Cart addition
    println(">> [BEGIN] Cart addition, press enter after cart is added")
    val cartAdditionResponse = Await.result(
      httpClient.singleRequest(Post(cartUri, CartAdditionEntity(store))),
      5.minutes
    )
    val cart = Await.result(Unmarshal(cartAdditionResponse.entity).to[ResultResponseEntity[Cart]], 5.minutes).result
    println(responsePrefix + cart.toString)
    printCartState(dittoClient, cart.id, store)
    println(">> [END] Cart addition\n")
    println(">> [BEGIN] Item category addition")
    // Item addition
    val itemCategoryAdditionResponse = Await.result(
      httpClient
        .singleRequest(
          Post(itemCategoryUri, ItemCategoryAdditionEntity("Lego Bat-mobile", "A simple item in a store, nothing else."))
        ),
      5.minutes
    )
    val itemCategory =
      Await.result(Unmarshal(itemCategoryAdditionResponse.entity).to[ResultResponseEntity[ItemCategory]], 5.minutes).result
    println(responsePrefix + itemCategory.toString)
    println(">> [END] Item category addition: press enter to continue")
    StdIn.readLine()
    println(">> [BEGIN] Catalog item addition")
    val catalogItemAdditionResponse = Await.result(
      httpClient.singleRequest(Post(catalogItemUri, CatalogItemAdditionEntity(itemCategory.id, store, Price(59.99, "EUR")))),
      5.minutes
    )
    val catalogItem =
      Await.result(Unmarshal(catalogItemAdditionResponse.entity).to[ResultResponseEntity[CatalogItem]], 5.minutes).result
    println(responsePrefix + catalogItem.toString)
    println(">> [END] Catalog item addition: press enter to continue")
    StdIn.readLine()
    println(">> [BEGIN] First item addition")
    val firstItemId = 0
    val firstItemAdditionResponse = Await.result(
      httpClient.singleRequest(Post(itemUri, ItemAdditionEntity(firstItemId, catalogItem.id, store))),
      5.minutes
    )
    println(
      responsePrefix +
      Await.result(Unmarshal(firstItemAdditionResponse.entity).to[ResultResponseEntity[Item]], 5.minutes).result.toString
    )
    println(">> [END] First item addition")
    println(">> [BEGIN] Second item addition")
    val secondItemId = 1
    val secondItemAdditionResponse = Await.result(
      httpClient.singleRequest(Post(itemUri, ItemAdditionEntity(secondItemId, catalogItem.id, store))),
      5.minutes
    )
    println(
      responsePrefix +
      Await.result(Unmarshal(secondItemAdditionResponse.entity).to[ResultResponseEntity[Item]], 5.minutes).result.toString
    )
    println(">> [END] Second item addition: press enter to continue")
    StdIn.readLine()
    (cart.id, itemCategory.id, catalogItem.id, firstItemId, secondItemId)
  }

  private def customerLogin(httpClient: HttpExt)(using ExecutionContext, ClassicActorSystem): (String, String) = {
    println(">> [BEGIN] Customer registration")
    val customer = "luigi@mail.com"
    val password = "Password1!"
    val customerRegistrationResponse = Await.result(
      httpClient.singleRequest(Post(customerUri, CustomerRegistrationEntity(customer, "luigi", "Luigi", "Rossi", password))),
      5.minutes
    )
    println(
      responsePrefix +
      Await
        .result(Unmarshal(customerRegistrationResponse.entity).to[ResultResponseEntity[Customer]], 5.minutes)
        .result
        .toString
    )
    println(">> [END] Customer registration")
    println(">> [BEGIN] Customer login")
    val customerLoginResponse = Await.result(
      httpClient.singleRequest(Put(customerUri + "/login", CustomerLoginEntity(customer, password))),
      5.minutes
    )
    println(
      responsePrefix +
      Await
        .result(Unmarshal(customerLoginResponse.entity).to[ResultResponseEntity[Customer]], 5.minutes)
        .result
        .toString
    )
    println(">> [END] Customer login: press enter to continue")
    StdIn.readLine()
    (customer, password)
  }

  private def associateCart(
    httpClient: HttpExt,
    dittoClient: DittoClient,
    cartId: Long,
    store: Long,
    customer: String
  )(
    using
    ExecutionContext,
    ClassicActorSystem
  ): Unit = {
    val cartAssociationResponse = Await.result(
      httpClient.singleRequest(Put(cartUri + "/associate", CartAssociationEntity(cartId, store, customer))),
      5.minutes
    )
    println(
      "> [RESPONSE] " +
      Await.result(Unmarshal(cartAssociationResponse.entity).to[ResultResponseEntity[Cart]], 5.minutes).result.toString
    )
    printCartState(dittoClient, cartId, store)
  }

  private def lockCart(
    httpClient: HttpExt,
    dittoClient: DittoClient,
    cartId: Long,
    store: Long
  )(
    using
    ExecutionContext,
    ClassicActorSystem
  ): Unit = {
    val cartLockResponse = Await.result(
      httpClient.singleRequest(Put(cartUri + "/lock", CartLockEntity(cartId, store))),
      5.minutes
    )
    println(
      "> [RESPONSE] " + Await.result(Unmarshal(cartLockResponse.entity).to[ResultResponseEntity[Cart]], 5.minutes).result.toString
    )
    printCartState(dittoClient, cartId, store)
  }

  private def showItem(
    client: HttpExt,
    itemId: Long,
    kind: Long,
    store: Long
  )(
    using
    ExecutionContext,
    ClassicActorSystem
  ): Unit = {
    val showItemResponse = Await.result(
      client.singleRequest(
        Get(
          itemUri,
          ItemShowEntity(itemId, kind, store)
        )
      ),
      5.minutes
    )
    println(
      responsePrefix
      + Await.result(Unmarshal(showItemResponse.entity).to[ResultResponseEntity[Item]], 5.minutes).result.toString
    )
  }

  private def shutdown(
    httpClient: HttpExt,
    dittoClient: DittoClient,
    store: Long,
    cartId: Long,
    itemCategoryId: Long,
    catalogItemId: Long,
    firstItemId: Long,
    secondItemId: Long,
    customer: String,
    password: String
  )(
    using
    ExecutionContext,
    ClassicActorSystem
  ): Unit = {
    // Customer de-registration
    println(">> [BEGIN] Customer de-registration")
    val customerDeregistrationResponse = Await.result(
      httpClient.singleRequest(Delete(customerUri, CustomerDeregistrationEntity(customer, password))),
      5.minutes
    )
    println(
      responsePrefix +
      Await.result(Unmarshal(customerDeregistrationResponse.entity).to[ResultResponseEntity[Unit]], 5.minutes).result.toString
    )
    println(">> [END] Customer de-registration")
    // Items deletion
    println(">> [BEGIN] First item removal")
    val firstItemRemovalResponse = Await.result(
      httpClient.singleRequest(
        Delete(
          itemUri,
          ItemRemovalEntity(firstItemId, catalogItemId, store)
        )
      ),
      5.minutes
    )
    println(
      responsePrefix +
      Await.result(Unmarshal(firstItemRemovalResponse.entity).to[ResultResponseEntity[Unit]], 5.minutes).result.toString
    )
    println(">> [END] First item removal")
    println(">> [BEGIN] Second item removal started")
    val secondItemRemovalResponse = Await.result(
      httpClient.singleRequest(
        Delete(
          itemUri,
          ItemRemovalEntity(secondItemId, catalogItemId, store)
        )
      ),
      5.minutes
    )
    println(
      responsePrefix +
      Await.result(Unmarshal(secondItemRemovalResponse.entity).to[ResultResponseEntity[Unit]], 5.minutes).result.toString
    )
    println(">> [END] Second item removal")
    // Catalog item removal
    println(">> [BEGIN] Catalog item removal started")
    val catalogItemRemovalResponse = Await.result(
      httpClient.singleRequest(Delete(catalogItemUri, CatalogItemRemovalEntity(catalogItemId, store))),
      5.minutes
    )
    println(
      responsePrefix +
      Await.result(Unmarshal(catalogItemRemovalResponse.entity).to[ResultResponseEntity[Unit]], 5.minutes).result.toString
    )
    println(">> [END] Catalog item removal")
    // Item category removal
    println(">> [BEGIN] Item category removal")
    val itemCategoryRemovalResponse = Await.result(
      httpClient.singleRequest(Delete(itemCategoryUri, ItemCategoryRemovalEntity(itemCategoryId))),
      5.minutes
    )
    println(
      responsePrefix +
      Await.result(Unmarshal(itemCategoryRemovalResponse.entity).to[ResultResponseEntity[Unit]], 5.minutes).result.toString
    )
    println(">> [END] Item category removal")
    println(">> [BEGIN] Cart removal")
    // Cart removal
    val cartRemovalResponse = Await.result(
      httpClient.singleRequest(Delete(cartUri, CartRemovalEntity(cartId, store))),
      5.minutes
    )
    println(
      responsePrefix
      + Await.result(Unmarshal(cartRemovalResponse.entity).to[ResultResponseEntity[Unit]], 5.minutes).result.toString
    )
    println(">> [END] Cart removal")
    println("! [BEGIN] Cart missing as digital twin in Ditto service")
    try {
      dittoClient
        .twin
        .forId(ThingId.of(s"io.github.pervasivecats:cart-$cartId-$store"))
        .retrieve
        .toCompletableFuture
        .get()
      throw IllegalStateException()
    } catch {
      case c: ExecutionException =>
        c.getCause match {
          case t: ThingNotAccessibleException if t.getHttpStatus === HttpStatus.NOT_FOUND =>
            println("! [RESPONSE] Cart not found")
          case _ => throw IllegalStateException()
        }
      case _ => throw IllegalStateException()
    }
    println("! [END] Press enter to continue")
    StdIn.readLine()
  }

  private def printCartState(client: DittoClient, cartId: Long, store: Long): Unit = {
    println("! [BEGIN] Cart state as digital twin in Ditto service")
    val thing =
      client
        .twin
        .forId(ThingId.of(s"io.github.pervasivecats:cart-$cartId-$store"))
        .retrieve
        .toCompletableFuture
        .get()
    println(s"! [RESPONSE] Cart(${thing.getEntityId.get()}, ${thing.getAttributes.get()})")
    println("! [END] Press enter to continue")
    StdIn.readLine()
  }

  @main
  def main(dittoUsername: String, dittoPassword: String): Unit = {
    val actorSystem: ActorSystem[Unit] = ActorSystem[Unit](Behaviors.empty[Unit], "root_actor")
    given ClassicActorSystem = actorSystem.classicSystem
    given ExecutionContext = actorSystem.executionContext

    val dittoClient: DittoClient = DittoSetup(dittoUsername, dittoPassword)
    val httpClient: HttpExt = Http()

    // Store initialization
    println("\n> [BEGIN] Store initialization\n")
    val store = 999
    val (cartId, itemCategoryId, catalogItemId, firstItemId, secondItemId) = initializeStore(httpClient, dittoClient, store)
    println("> [END] Store initialization\n\n")

    // Customer entering store
    println("> [BEGIN] Customer entering store\n")
    val (customer, password) = customerLogin(httpClient)
    println("> [END] Customer entering store\n\n")

    // Cart moved - Wait for alarm raised
    println("> [BEGIN] Customer starts dragging cart, press enter after alarm is raised\n")
    dittoClient
      .live
      .forId(ThingId.of(s"io.github.pervasivecats:cart-$cartId-$store"))
      .message[String]
      .from
      .subject("cartMoved")
      .send()
    StdIn.readLine()
    println("> [END] Customer stops dragging cart\n\n")

    // Cart association
    println("> [BEGIN] Cart association, press enter after cart is associated\n")
    associateCart(httpClient, dittoClient, cartId, store, customer)
    println("> [END] Cart association\n\n")

    // Cart moved - No alarm is raised
    println("> [BEGIN] Customer dragging cart: press enter after noting that no alarm is raised\n")
    dittoClient
      .live
      .forId(ThingId.of(s"io.github.pervasivecats:cart-$cartId-$store"))
      .message[String]
      .from
      .subject("cartMoved")
      .send()
    StdIn.readLine()
    println("> [END] Customer dragging cart\n\n")

    // Catalog item lifting

    // First item insertion into cart
    println("> [BEGIN] Customer inserts item into cart: press enter after noting that it is indeed happened\n")
    dittoClient
      .live
      .forId(ThingId.of(s"io.github.pervasivecats:cart-$cartId-$store"))
      .message[String]
      .from
      .subject("itemInsertedIntoCart")
      .payload(JsObject("catalogItem" -> catalogItemId.toJson, "itemId" -> firstItemId.toJson).compactPrint)
      .send()
    StdIn.readLine()
    showItem(httpClient, firstItemId, catalogItemId, store)
    println("> [END] Customer inserts item into cart\n\n")

    // First item removal with drop system

    // Second item neared to anti-theft system

    // Cart locked
    println("> [BEGIN] Cart locking, press enter after cart is locked\n")
    lockCart(httpClient, dittoClient, cartId, store)
    println("> [END] Cart locking\n\n")

    // System shutdown
    println("> [BEGIN] System shutdown\n")
    shutdown(httpClient, dittoClient, store, cartId, itemCategoryId, catalogItemId, firstItemId, secondItemId, customer, password)
    println("> [END] System shutdown")
    actorSystem.whenTerminated.foreach[Unit](_ => sys.exit(0))
    actorSystem.terminate()
  }
}