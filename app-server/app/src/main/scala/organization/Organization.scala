package com.azavea.rf.organization

import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Success, Failure, Try}
import slick.lifted.Query
import java.sql.Timestamp
import org.postgresql.util.PSQLException

import com.lonelyplanet.akka.http.extensions.{PageRequest, Order}

import com.azavea.rf.datamodel.latest.schema.tables.{
  Users,
  Organizations,
  OrganizationsRow,
  UsersToOrganizations,
  UsersToOrganizationsRow
}

import com.azavea.rf.utils.{
  Database,
  PaginatedResponse,
  UserErrorException
}
import com.azavea.rf.user.UserRoles


case class UserWithRole(id: String, role: String, createdAt: Timestamp, modifiedAt: Timestamp)


case class UserWithRoleCreate(id: String, role: String) {
  def toUserWithRole(): UserWithRole = {
    val now = new Timestamp((new java.util.Date()).getTime())
    UserWithRole(id, role, now, now)
  }
}


case class OrganizationsRowCreate(name: String) {
  def toOrganizationsRow(): OrganizationsRow = {
    val id = java.util.UUID.randomUUID()
    val now = new Timestamp((new java.util.Date()).getTime())
    OrganizationsRow(id, now, now, name)
  }
}


object OrganizationService {
  def applyOrgSort(
    query: Query[Organizations, Organizations#TableElementType, Seq],
    sortMap: Map[String, Order]
  )(implicit database: Database, ec: ExecutionContext):
      Query[Organizations, Organizations#TableElementType, Seq] = {
    import database.driver.api._

    sortMap.headOption match {
      case Some(("id", order)) =>
        order match {
          case Order.Asc => applyOrgSort(query.sortBy(_.id.asc), sortMap.tail)
          case Order.Desc => applyOrgSort(query.sortBy(_.id.desc), sortMap.tail)
        }
      case Some(("name", order)) =>
        order match {
          case Order.Asc => applyOrgSort(query.sortBy(_.name.asc), sortMap.tail)
          case Order.Desc => applyOrgSort(query.sortBy(_.name.desc), sortMap.tail)
        }
      case Some(("modified", order)) =>
        order match {
          case Order.Asc => applyOrgSort(query.sortBy(_.modifiedAt.asc), sortMap.tail)
          case Order.Desc => applyOrgSort(query.sortBy(_.modifiedAt.desc), sortMap.tail)
        }
      case Some(("created", order)) =>
        order match {
          case Order.Asc => applyOrgSort(query.sortBy(_.createdAt.asc), sortMap.tail)
          case Order.Desc => applyOrgSort(query.sortBy(_.createdAt.desc), sortMap.tail)
        }
      case Some((_, order)) => applyOrgSort(query, sortMap.tail)
      case _ => query
    }
  }

  def applyUserRoleSort(
    query: Query[UsersToOrganizations, UsersToOrganizations#TableElementType, Seq],
    sortMap: Map[String, Order]
  )(implicit database: Database, ec: ExecutionContext):
      Query[UsersToOrganizations, UsersToOrganizations#TableElementType, Seq] = {
    import database.driver.api._

    sortMap.headOption match {
      case Some(("id", order)) =>
        order match {
          case Order.Asc => applyUserRoleSort(query.sortBy(_.userId.asc), sortMap.tail)
          case Order.Desc => applyUserRoleSort(query.sortBy(_.userId.desc), sortMap.tail)
        }
      case Some(("role", order)) =>
        order match {
          case Order.Asc => applyUserRoleSort(query.sortBy(_.role.asc), sortMap.tail)
          case Order.Desc => applyUserRoleSort(query.sortBy(_.role.desc), sortMap.tail)
        }
      case Some(("modified", order)) =>
        order match {
          case Order.Asc => applyUserRoleSort(query.sortBy(_.modifiedAt.asc), sortMap.tail)
          case Order.Desc => applyUserRoleSort(query.sortBy(_.modifiedAt.desc), sortMap.tail)
        }
      case Some(("created", order)) =>
        order match {
          case Order.Asc => applyUserRoleSort(query.sortBy(_.createdAt.asc), sortMap.tail)
          case Order.Desc => applyUserRoleSort(query.sortBy(_.createdAt.desc), sortMap.tail)
        }
      case Some((_, order)) => applyUserRoleSort(query, sortMap.tail)
      case _ => query
    }
  }

  def getOrganizationList(page: PageRequest)(implicit database: Database, ec: ExecutionContext):
      Future[PaginatedResponse[OrganizationsRow]] = {
    import database.driver.api._

    val organizationsQueryResult = database.db.run {
      applyOrgSort(Organizations, page.sort)
        .drop(page.offset * page.limit)
        .take(page.limit)
        .result
    }
    val totalOrganizationsQuery = database.db.run {
      Organizations.length.result
    }

    for {
      totalOrganizations <- totalOrganizationsQuery
      organizations <- organizationsQueryResult
    } yield {
      val hasNext = (page.offset + 1) * page.limit < totalOrganizations // 0 indexed page offset
      val hasPrevious = page.offset > 0
      PaginatedResponse(totalOrganizations, hasPrevious, hasNext,
        page.offset, page.limit, organizations)
    }
  }

  def getOrganization(id: java.util.UUID)(implicit database: Database):
      Future[Option[OrganizationsRow]] = {
    import database.driver.api._

    database.db.run {
      Organizations.filter(_.id === id).result.headOption
    }
  }

  def createOrganization(
    org: OrganizationsRowCreate
  )(implicit database: Database, ec: ExecutionContext): Future[Try[OrganizationsRow]] = {
    import database.driver.api._

    val rowInsert = org.toOrganizationsRow()

    database.db.run {
      Organizations.forceInsert(rowInsert).asTry
    } map {
      case Success(res) => {
        res match {
          case 1 => Success(rowInsert)
          case _ => Failure(
            new Exception(
              s"Unexpected result from database when inserting organization: $res"
            )
          )
        }
      }
      case Failure(e) => {
        e match {
          case e: PSQLException => {
            Failure(new UserErrorException("Organization already exists"))
          }
          case _ => Failure(e)
        }
      }
    }
  }

  def updateOrganization(
    org: OrganizationsRow, id: java.util.UUID
  )(implicit database: Database, ec: ExecutionContext): Future[Try[Int]] = {
    import database.driver.api._

    val now = new Timestamp((new java.util.Date()).getTime())
    val updateQuery = for {
      updateorg <- Organizations.filter(_.id === id)
    } yield (
      updateorg.name, updateorg.modifiedAt
    )
    database.db.run {
      updateQuery.update((org.name, now)).asTry
    } map {
      case Success(res) => {
        res match {
          case 1 => Success(1)
          case _ => Failure(new Exception("Error while updating organization: Unexpected result"))
        }
      }
      case Failure(e) => Failure(e)
    }
  }

  def getOrganizationUsers(
    page: PageRequest, id: java.util.UUID
  )(implicit database: Database, ec: ExecutionContext): Future[PaginatedResponse[UserWithRole]] = {
    import database.driver.api._

    val getOrgUsersResult = database.db.run {
      applyUserRoleSort(UsersToOrganizations.filter(_.organizationId === id), page.sort)
        .drop(page.offset * page.limit)
        .take(page.limit)
        .result
    } map {
      rels => rels.map(rel => UserWithRole(rel.userId, rel.role, rel.createdAt, rel.modifiedAt))
    }

    val totalOrgUsersResult = database.db.run {
      UsersToOrganizations.filter(_.organizationId === id).length.result
    }

    for {
      totalOrgUsers <- totalOrgUsersResult
      orgUsers <- getOrgUsersResult
    } yield {
      val hasNext = (page.offset + 1) * page.limit < totalOrgUsers // 0 indexed page offset
      val hasPrevious = page.offset > 0
      PaginatedResponse(totalOrgUsers, hasPrevious, hasNext, page.offset, page.limit, orgUsers)
    }
  }

  def getOrganizationUser(
    orgId: java.util.UUID, userId: String
  )(implicit database: Database, ex: ExecutionContext): Future[Option[UserWithRole]] = {
    import database.driver.api._

    val getOrgUserQuery = for {
      relationship <- UsersToOrganizations.filter(_.userId === userId)
        .filter(_.organizationId === orgId)
      user <- Users.filter(_.id === relationship.userId)
    } yield (user.id, relationship.role, relationship.createdAt, relationship.modifiedAt)
    database.db.run {
      getOrgUserQuery.result.headOption
    } map {
      case Some(tuple) => Option(UserWithRole.tupled(tuple))
      case _ => None
    }
  }

  def addUserToOrganization(
    userWithRoleCreate: UserWithRoleCreate, orgId: java.util.UUID
  )(implicit database: Database, ex: ExecutionContext): Future[Try[UserWithRole]] = {
    import database.driver.api._

    val userWithRole = userWithRoleCreate.toUserWithRole()

    val insertRow = UsersToOrganizationsRow(
      userWithRole.id, orgId, userWithRole.role, userWithRole.createdAt, userWithRole.modifiedAt
    )

    database.db.run {
      UsersToOrganizations.forceInsert(insertRow).asTry
    } map {
      case Success(user) => Success(userWithRole)
      case Failure(_) => throw new UserErrorException("User is already in the organization")
    }
  }

  def getUserOrgRole(
    userId: String, orgId: java.util.UUID
  )(implicit database: Database, ex: ExecutionContext): Future[Option[UserWithRole]] = {
    import database.driver.api._
    database.db.run {
      UsersToOrganizations.filter(
        rel => rel.userId === userId && rel.organizationId === orgId
      ).result.headOption
    } map {
      case Some(rel) => Some(UserWithRole(rel.userId, rel.role, rel.createdAt, rel.modifiedAt))
      case _ => None
    }
  }

  def deleteUserOrgRole(
    userId: String, orgId: java.util.UUID
  )(implicit database: Database): Future[Int] = {
    import database.driver.api._
    database.db.run {
      UsersToOrganizations.filter(
        rel => rel.userId === userId && rel.organizationId === orgId
      ).delete
    }
  }

  def updateUserOrgRole(
    userWithRole: UserWithRole, orgId: java.util.UUID, userId: String
  )(implicit database: Database): Future[Try[Int]] = {
    import database.driver.api._

    userWithRole.role match {
      case UserRoles(_) => {
        val now = new Timestamp((new java.util.Date()).getTime())

        val rowUpdate = for {
          relationship <- UsersToOrganizations.filter(
            rel => rel.userId === userId && rel.organizationId === orgId
          )
        } yield (
          relationship.modifiedAt, relationship.role
        )

        database.db.run {
          rowUpdate.update(
            (now, userWithRole.role)
          ).asTry
        }
      }
      case invalidRole: String => throw new UserErrorException(
        s"$invalidRole is not a valid User role"
      )
    }
  }
}