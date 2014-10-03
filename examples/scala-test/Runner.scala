package org.sample.test

import io.prediction.controller._

object Runner {

  def main(args: Array[String]) {

    Workflow.run(
       dataSourceClassOpt = Some(classOf[MyDataSource]),
       params = WorkflowParams(
         verbose = 3,
         batch = "MyDataSource")
    )

  }

}
