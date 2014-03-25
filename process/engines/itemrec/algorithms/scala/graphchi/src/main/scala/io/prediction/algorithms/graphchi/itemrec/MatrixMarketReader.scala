package io.prediction.algorithms.graphchi.itemrec

import breeze.linalg._
import grizzled.slf4j.Logger
import scala.io.Source

object MatrixMarketReader {

  val logger = Logger(MatrixMarketReader.getClass)

  /* read dense matrix market from file and return DenseMatrix object */
  def readDense(path: String): DenseMatrix[Double] = {
    val matrixFile = Source.fromFile(path)
    // skip line starts with %
    // skip empty line
    val lines = matrixFile.getLines()
      .filter(line => (line.length != 0) && (!line.startsWith("%")))

    // first line is matrix size
    if (lines.hasNext) {
      val line = lines.next()
      val size = line.split("""\s+""")

      // matrix market dense format is column oriented
      /* eg:
       * 2 3
       * 1
       * 2
       * 3
       * 4
       * 5
       * 6
       * becomes
       * 1 4
       * 2 5
       * 3 6
       */
      val (colNum, rowNum): (Int, Int) = try {
        (size(0).toInt, size(1).toInt)
      } catch {
        case e: Exception =>
          throw new RuntimeException(s"Cannot extract matrix size from the line: ${line}. ${e}")
      }

      logger.debug(s"${rowNum}, ${colNum}")
      val matrix = DenseMatrix.zeros[Double](rowNum, colNum)

      var r = 0
      var c = 0
      lines.foreach { line =>
        if (c >= colNum) {
          throw new RuntimeException(s"Number of elements greater than the defined size: ${rowNum} ${colNum}")
        } else {

          logger.debug(s"${r}, ${c} = ${line}")
          try {
            matrix(r, c) = line.toDouble
          } catch {
            case e: Exception =>
              throw new RuntimeException(s"Cannot convert line: ${line} to double. ${e}")
          }
          r += 1
          if (r == rowNum) {
            r = 0
            c += 1
          }
        }
      }
      // c must == colNum when finish
      if (c < colNum) {
        throw new RuntimeException(s"Number of elements smaller than the defined size: ${rowNum} ${colNum}")
      }
      logger.debug(matrix)
      matrix
    } else {
      DenseMatrix.zeros[Double](0, 0)
    }
  }

}