package com.azavea.rf.image

import java.util.UUID

import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.testkit.{ScalatestRouteTest, RouteTestTimeout}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.{HttpEntity, ContentTypes}
import akka.actor.ActorSystem
import concurrent.duration._
import spray.json._

import com.azavea.rf.utils.Config
import com.azavea.rf.{DBSpec, Router}
import com.azavea.rf.datamodel.latest.schema.tables._
import com.azavea.rf.scene._
import com.azavea.rf.datamodel.enums._
import com.azavea.rf.utils.PaginatedResponse
import com.azavea.rf.AuthUtils
import java.sql.Timestamp
import java.time.Instant


class ImageSpec extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with Config
    with Router
    with DBSpec {
  implicit val ec = system.dispatcher

  implicit def database = db
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(DurationInt(20).second)

  val authorization = AuthUtils.generateAuthHeader("Default")
  val baseImagePath = "/api/images/"
  val publicOrgId = UUID.fromString("dfac6307-b5ef-43f7-beda-b9f208bb7726")

  val newSceneDatasource1 = CreateScene(
    publicOrgId, 0, PUBLIC, 20.2f, List("Test", "Public", "Low Resolution"), "TEST_ORG",
    Map("instrument type" -> "satellite", "splines reticulated" -> 0):Map[String, Any], None,
    Some(Timestamp.from(Instant.parse("2016-09-19T14:41:58.408544Z"))),
    PROCESSING, PROCESSING, PROCESSING, None, None, "test scene image spec 1",
    List(): List[SceneImage], None, List(): List[SceneThumbnail]
  )


  "/api/images/{uuid}" should {

    "return a 404 for non-existent image" in {
      Get(s"${baseImagePath}${publicOrgId}") ~> imageRoutes ~> check {
        status shouldEqual StatusCodes.NotFound
      }
    }

    "return a image" ignore {
      val imageId = ""
      Get(s"${baseImagePath}${imageId}/") ~> imageRoutes ~> check {
        responseAs[ImagesRow]
      }
    }

    "update a image" ignore {
      // Add change to image here
    }

    "delete a image" ignore {
      val imageId = ""
      Delete(s"${baseImagePath}${imageId}/") ~> imageRoutes ~> check {
        status shouldEqual StatusCodes.NoContent
      }
    }
  }

  "/api/images/" should {
    "not require authentication" in {
      Get("/api/images/") ~> imageRoutes ~> check {
        responseAs[PaginatedResponse[ImagesRow]]
      }
    }

    "create a image successfully once authenticated" in {
      // Create scene first via API because we need the ID
      Post("/api/scenes/").withHeadersAndEntity(
        List(authorization),
        HttpEntity(
          ContentTypes.`application/json`,
          newSceneDatasource1.toJson(createSceneFormat).toString()
        )
      ) ~> sceneRoutes ~> check {
        val sceneId = responseAs[SceneWithRelated].id

        val newImageDatasource1 = CreateImage(
          publicOrgId, 1024, PUBLIC, "test-image.png", "s3://public/s3/test-image.png",
          sceneId, List("red, green, blue"),
          Map("instrument type" -> "satellite", "splines reticulated" -> 0):Map[String, Any]
        )

        Post("/api/images/").withHeadersAndEntity(
          List(authorization),
          HttpEntity(
            ContentTypes.`application/json`,
            newImageDatasource1.toJson(createImageFormat).toString()
          )
        ) ~> imageRoutes ~> check {
          responseAs[ImagesRow]
        }
      }
    }

    "filter by one organization correctly" in {
      Get(s"$baseImagePath?organization=${publicOrgId}") ~> imageRoutes ~> check {
        responseAs[PaginatedResponse[ImagesRow]].count shouldEqual 1
      }
    }

    "filter by two organizations correctly" in {
      val url = s"$baseImagePath?organization=${publicOrgId}&organization=dfac6307-b5ef-43f7-beda-b9f208bb7725"
      Get(url) ~> imageRoutes ~> check {
        responseAs[PaginatedResponse[ImagesRow]].count shouldEqual 1
      }
    }

    "filter by one (non-existent) organizations correctly" in {
      val url = s"$baseImagePath?organization=dfac6307-b5ef-43f7-beda-b9f208bb7725"
      Get(url) ~> imageRoutes ~> check {
        responseAs[PaginatedResponse[ImagesRow]].count shouldEqual 0
      }
    }

    "filter by min bytes correctly" in {
      val url = s"$baseImagePath?minRawDataBytes=10"
      Get(url) ~> imageRoutes ~> check {
        responseAs[PaginatedResponse[ImagesRow]].count shouldEqual 1
      }
    }

    "filter by scene correctly" in {
      Get("/api/scenes/") ~> sceneRoutes ~> check {
        val scenes = responseAs[PaginatedResponse[SceneWithRelated]]
        val sceneId = scenes.results.head.id

        val url = s"$baseImagePath?scene=$sceneId"
        Get(url) ~> imageRoutes ~> check {
          responseAs[PaginatedResponse[ImagesRow]].count shouldEqual 1
        }
      }
    }
  }
}
