package myorg

import org.apache.predictionio.controller.Engine
import org.apache.predictionio.controller.IEngineFactory
import org.apache.predictionio.engines.itemrec.EventsDataSource
import org.apache.predictionio.engines.itemrec.ItemRecPreparator
import org.apache.predictionio.engines.itemrec.NCItemBasedAlgorithm

object TempFilterEngine extends IEngineFactory {
  def apply() = {
    new Engine(
      classOf[EventsDataSource],
      classOf[ItemRecPreparator],
      Map("ncMahoutItemBased" -> classOf[NCItemBasedAlgorithm]),
      classOf[TempFilter]
    )
  }
}
