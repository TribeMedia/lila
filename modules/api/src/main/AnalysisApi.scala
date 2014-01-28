package lila.api

import chess.format.pgn.Pgn
import play.api.libs.json._

import lila.analyse.{ Analysis, AnalysisRepo }
import lila.common.PimpedJson._
import lila.db.api._
import lila.db.Implicits._
import lila.game.{ GameRepo, PgnDump }
import lila.hub.actorApi.{ router ⇒ R }

private[api] final class AnalysisApi(
    makeUrl: Any ⇒ Fu[String],
    pgnDump: PgnDump) {

  private def makeNb(nb: Option[Int]) = math.min(100, nb | 10)

  def list(nb: Option[Int]): Fu[JsObject] = AnalysisRepo recent makeNb(nb) flatMap { as ⇒
    GameRepo games as.map(_.id) flatMap { games ⇒
      games.map { g ⇒
        as find (_.id == g.id) map { _ -> g }
      }.flatten.map {
        case (a, g) ⇒ pgnDump(g) zip
          makeUrl(R.Watcher(g.id, g.firstPlayer.color.name)) map {
            case (pgn, url) ⇒ (g, a, url, pgn)
          }
      }.sequenceFu map { tuples ⇒
        Json.obj(
          "list" -> JsArray(tuples map {
            case (game, analysis, url, pgn) ⇒ Json.obj(
              "game" -> GameApi.gameToJson(game, url, analysis.some),
              "analysis" -> AnalysisApi.analysisToJson(analysis, pgn)
            )
          })
        )
      }
    }
  }
}

private[api] object AnalysisApi {

  def analysisToJson(analysis: Analysis, pgn: Pgn) = JsArray(analysis.infoAdvices zip pgn.moves map {
    case ((info, adviceOption), move) ⇒ Json.obj(
      "ply" -> info.ply,
      "move" -> move.san,
      "eval" -> info.score.map(_.centipawns),
      "mate" -> info.mate,
      "variation" -> info.variation.isEmpty.fold(JsNull, info.variation mkString " "),
      "comment" -> adviceOption.map(_.makeComment(true, true))
    ).noNull
  })
}