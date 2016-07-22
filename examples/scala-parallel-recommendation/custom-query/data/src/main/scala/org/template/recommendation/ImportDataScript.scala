package org.template.recommendation

import org.apache.predictionio.{Event, EventClient
}
import scala.collection.JavaConverters._

import scala.io.Source

/**
 * @author Maxim Korolyov.
 */
object ImportDataScript extends App {

  /**
   * Imports users, movies and rate events if movielens database to pio.
   * first arg is mandatory - it is app access key
   * second arg is optional - it is pio engine url (default is `localhost:7070`)
   * @param args
   */
  override def main(args: Array[String]): Unit = {
    val accessKey = if (args.length == 0) {
      throw new IllegalArgumentException("access key should be passed")
    } else args(0)

    val engineUrl = if (args.length > 1) args(1) else "http://localhost:7070"
    implicit val client = new EventClient(accessKey, engineUrl)
    println(s"imported ${importMovies.size} movies")
    println(s"imported ${importUsers.size} users")
    println(s"imported ${importRateEvents.size} events")
  }

  /**
   * imports events to the pio server.
   * @return the events id list.
   */
  def importRateEvents(implicit client: EventClient): Iterator[_] =
    readCSV("data/u.data", "\t").flatMap { event =>
      val eventObj = event.lift
      (for {
        entityId ← eventObj(0)
        targetEntityId ← eventObj(1)
        rating ← eventObj(2)
      } yield new Event()
          .event("rate")
          .entityId(entityId)
          .entityType("user")
          .properties(javaMap("rating" → new java.lang.Double(rating)))
          .targetEntityId(targetEntityId)
          .targetEntityType("movie")
        ).map(client.createEvent)
    }

  def importUsers(implicit ec: EventClient): Iterator[_] =
    readCSV("data/u.user").flatMap { user ⇒
      val userObj = user.lift
      for {
        uId ← userObj(0)
        age ← userObj(1)
      } yield ec.setUser(uId, javaMap("age" → age))
    }

  /**
   * imports movies to pio server
   * @return the number if movies where imported
   */
  def importMovies(implicit client: EventClient): Iterator[Unit] = {
    readCSV("data/u.item").map { movie ⇒
      val movieObj = movie.lift
      val releaseYearOpt = movieObj(2)
        .flatMap(_.split("-").lift(2).map(_.toInt))
        .map(releaseYear ⇒ "creationYear" → new Integer(releaseYear))
      for {
        id ← movieObj(0)
        title ← movieObj(1).map(t ⇒ "title" → t)
        releaseYear ← releaseYearOpt
      } yield client.setItem(id, javaMap(title, releaseYear))
    }
  }

  private def javaMap(pair: (String, AnyRef)*) = Map(pair: _*).asJava

  /**
   * reads csv file into list of string arrays each of them represents splitted
   * with specified delimeter line
   * @param filename path to csv file
   * @param delimiter delimiter of the properties in the file
   * @return the list of string arrays made from every file line
   */
  private def readCSV(filename: String,
                      delimiter: String = "\\|") =
    Source.fromFile(filename, "UTF-8")
      .getLines()
      .map(_.split(delimiter).toVector)
}
