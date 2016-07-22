package myorg

import org.apache.predictionio.controller.LServing
import org.apache.predictionio.controller.Params
import org.apache.predictionio.engines.itemrec.Prediction
import org.apache.predictionio.engines.itemrec.Query
import scala.io.Source

case class TempFilterParams(val filepath: String) extends Params

class TempFilter(val params: TempFilterParams) 
    extends LServing[TempFilterParams, Query, Prediction] {
  override def serve(query: Query, predictions: Seq[Prediction]): Prediction = {
    val disabledIids: Set[String] = Source.fromFile(params.filepath)
      .getLines()
      .toSet

    val prediction = predictions.head
    // prediction.items is a list of (item_id, score)-tuple
    prediction.copy(items = prediction.items.filter(e => !disabledIids(e._1)))
  }
}
