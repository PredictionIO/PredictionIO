# Using predictionIO to run predictions using Yahoo Finance

This tutorial assumes you have gone through the quickstart guide for
PredictionIO.

## Setting up the environment

### Step 1: Get your Pandas
Where: PredictionIO-Python-SDK

Run: sudo pip install pandas==0.13.1

pip command not found? install python from curl -O

https://bootstrap.pypa.io/get-pip.py

python get-pip.py

and then run sudo pip install pandas

### Step 2: Edit import_yahoo.py
Where: PredictionIO-Python-SDK/examples/import_yahoo.py

At the end of file, find the following:
```
if __name__ == '__main__':
  #import_all(app_id=?)
  import_data_with_gaps(app_id=1)
  #import_one(app_id=1)
```
And, uncomment the first import, replacing app_id with your own id. Next, comment the second import statement (import_data_with_gaps).

### Step 3: Import Yahoo Finance data.
Where: PredictionIO-Python-SDK/examples

Run: sudo python -m examples.import_yahoo


### Step 4: Now make the distribution of PredictionIO
Where: cloned PredictionIO directory (with source code, make sure code is updated, git pull)
```
./make-distribution.sh
```
### Step 5: Edit scala-stock
go to examples/scala-stock/src/main/scala

Edit YahooDataSource.scala

Go to end of file to PredefinedDSP object

Edit app_id to match the one from step 2

### Step 6: Run scala-stock
Go to PredictionIO/examples/scala-stock

Now type:
```
../../bin/pio run --asm org.apache.predictionio.examples.stock.YahooDataSourceRun -- --master <Your spark master address found at http:local8080> --driver-memory <4-12G>
```
### Step 7: Open dashboard and view results
In PredictionIO folder

Type /bin/pio dashboard

go to url: http://localhost:9000 to view output


---
#PredictionIO Scala Stock Tutorial

##Implementing New Indicators

If you don't need to introduce any new indicators, skip this step. To introduce a new indicator, go to Indicators.scala and implement the `BaseIndicator` class. 'ShiftsIndicator' can serve as an example for how to do this:

```
abstract class BaseIndicator extends Serializable {
    def getTraining(logPrice: Series[DateTime, Double]): Series[DateTime, Double]
	def getOne(input: Series[DateTime, Double]): Double
	def minWindowSize(): Int
}
```
####`getTraining`
######Parameters:
This function takes in a Series of `logPrices` created from the closing prices of the YahooFinanace Data imported in YahooFinance.scala. `logPrices` refers to the logarithm of each price in the series.
######Functionality:
Performs a transformation over every value in the series to return a training series that will be used in the regression.
####`getOne()`
######Parameters:
This function takes in a smaller window of the price series representing the closing price of a single stock over `minWindowSize()`. The size of this series is defined by the return value of `minWindowSize()`
######Functionality:
This function performs the same function as `getTraining()` over a smaller window defined by the return value of `minWindowSize()`. 
####`minWindowSize()`
######Functionality:
Returns the minimum window sized required to do a single calculation of the Indicator function implemented by `getTraining()`.
For example, an indicator that requires seeing the previous day to make a calculation would have a `minWindowSize` of 2 days.

##Running New Indicators
To change which indicators to include in your run, open YahooDataSource.scala. This file imports the Yahoo finance data, prepares it, and runs the predictive engine.

Navigate to Workflow.run() in the `YahooDataSourceRun` object:
```
   Workflow.run(
      dataSourceClassOpt = Some(classOf[YahooDataSource]),
      dataSourceParams = dsp,
      preparatorClassOpt = Some(IdentityPreparator(classOf[DataSource])),
      algorithmClassMapOpt = Some(Map(
        //"" -> classOf[MomentumStrategy]
        "" -> classOf[RegressionStrategy]
      )),
      //algorithmParamsList = Seq(("", momentumParams)),
      algorithmParamsList = Seq(("", RegressionStrategyParams(Seq[(String, BaseIndicator)](
        ("RSI1", new RSIIndicator(period=1)), 
        ("RSI5", new RSIIndicator(period=5)), 
        ("RSI22", new RSIIndicator(period=22))), 
      200))),
      servingClassOpt = Some(FirstServing(classOf[EmptyStrategy])),
      metricsClassOpt = Some(classOf[BacktestingMetrics]),
      metricsParams = metricsParams,
      params = WorkflowParams(
        verbose = 0,
        saveModel = false,
```
####  `algorithmParamsList ()`
Edit these parameters to include any newly implemented indicator functions the predictive model should include. In this example you would change 'RSIIndicator' to your new indicator. If you would like to modify the strategy used, make sure to also change the constructor parameter, i.e. 'RegressionStrategyParams'.

### Viewing Your Results
To view the backtesting metrics, open up localhost:9000 in your browser and click on the 'HTML' button for the run you want to see the results of. On this page, the sharpe ratio is indicative of how effective your indicator implementations are in predictions.

### Tips and Tricks
To reduce run time run your code on a smaller dataset:  
1. Open `YahooDataSource.scala`  
2. Look for line `val dsp = PredefinedDSP.BigSP500` and comment it out
3. Uncomment out the line: `val dsp = PredefinedDSP.SmallSP500`  
4. In the `PredefinedDSP` object, switch the `appId` for `BigSP500`with the `appId` for `SmallSP500`. *Note: It should match the appID in your runnable  

To view standard output:  
1. Open localhost:8080 (spark)  
2. Click on the most recent worker  
3. Click on stdout or stderr to view the data in 100kb increments  


---


# OLD DOCUMENTATION

# (This doc is out-of-sync with the actual implementation)

## How to implement a stock prediction algorithm

### Fetch data from external source
You only need to do it once. (Unless you want to update your data.)

We provide a script which extracts historical data of SP500 stocks from Yahoo
and store the data in your local data storage. Make sure you have setup storage
according to [storage setup instructions](/README.md). Feel free to substitute
with your favorite stock data source.

Specify environment. (Temporary.)
```
$ cd $PIO_HOME/examples
$ set -a
$ source ../conf/pio-env.sh
$ set +a
```
where `$PIO_HOME` is the root directory of PredictionIO's code tree.

Run the fetch script.
```
$ ../sbt/sbt "runMain org.apache.predictionio.examples.stock.FetchMain"
```
As SP500 constituents change all the time, the hardcoded list may not reflect
the current state and the script may fail to extract delisted tickers. Whilst
the stock engine is designed to accomodate missing / incomplete data, you may as
well update the ticker list in [stock engine
settings](/engines/src/main/scala/stock/Stock.scala).

### High Level Description
A stock prediction algorithms employs a *rolling window* estimation method. For
a given window with `testingWindowSize`, the algorithm trains a model with data
in the window, and then the model is evaluated with another set of data.

For example, it builds a model with data from Aug 31, 2012 to Aug 31, 2013, and
evaluate the model with data from Sept 1, 2013 to Oct 1, 2013. And then, the
training is rolled one month forward: model training with data from Sept 30,
2012 to Sept 30, 2013, and evaluation with data from Oct 1, 2013 to Nov 1, 2013,
and so on.

Training Data | Testing Data
--------------|-------------
2012-08-31 -> 2013-08-31 | 2013-09-01 -> 2013-10-01
2012-09-30 -> 2013-09-30 | 2013-10-01 -> 2013-11-01
2012-10-31 -> 2013-10-31 | 2013-11-01 -> 2013-12-01
2012-11-30 -> 2013-11-30 | 2013-12-01 -> 2014-01-01
2012-12-31 -> 2013-12-31 | 2014-01-01 -> 2014-02-01
... | ...

For an algorithm developer, the task is to create a model with the training
data, and then make prediction based on testing data.

### Key Data Structures

#### Training Data
[Training data](Data.scala) has 4 main fields. `tickers` is the list of tickers
that is used for training. `mktTicker` is the ticker of market, for calculating
beta related metrics. `timeIndex` is a list of dates representing the time
window used for training. `price` is a map from ticker to array of stock price,
the array is of the same length as `timeIndex`. Notice that only tickers that
are active through out the whole time window is included, i.e. for example, if
the `TrainingData`'s time window is from 2012-01-01 to 2013-01-01, Facebook (FB)
will not be included in the training data.

```scala
class TrainingData(
  val tickers: Seq[String],
  val mktTicker: String,
  val timeIndex: Array[DateTime],
  val price: Array[(String, Array[Double])])
```

#### Query
[Query](Data.scala) is the input for prediction. Most of the fields resemble
`TrainingData`, with an additional field `tomorrow` indicating the date of the
prediction output. Algorithm builders take this as input, together with the
trained model created with `TrainingData` to make prediction.

```scala
class Query(
  val mktTicker: String,
  val tickerList: Seq[String],
  val timeIndex: Array[DateTime],
  val price: Array[(String, Array[Double])],
  val tomorrow: DateTime)
```

#### Target
[Target](Data.scala) is the output for prediction. It is essentially a map from
ticker to predicted return. Notice that the prediction need not to match
`Query`, if an algorithm cannot make prediction some symbols, it can just leave
them out.

### The Algorithm
Stock prediction algorithms should extends [StockAlgorithm](Stock.scala) class.

```scala
abstract class StockAlgorithm[P <: Params : ClassTag, M : ClassTag]
  extends LAlgorithm[P, TrainingData, M, Query, Target] {
  def train(trainingData: TrainingData): M
  def predict(model: M, query: Query): Target
}
```

#### RegressionAlgorithm
[RegressionAlgrotihm](RegressionAlgorithm.scala) creates a linear model for each
stock using a vector comprised of the 1-day, 1-week, and 1-month return of the
stock.

#### RandomAlgorithm
[RandomAlgorithm](RandomAlgorithm.scala) produces a gaussian random variable
with scaling and drift.

### Evaluation: Backtesting Metrics
This is the most common method in quantitative equity research. We test the
stock prediction algorithm against historical data. For each day in the
evaluation period, we open or close positions according to the prediction of the
algorithm. This allows us to simulate the daily P/L, volatility, and drawdown of
the prediction algorithm.

[BacktestingMetrics](BacktestingMetrics.scala) takes three parameters:
`enterThreshold` the minimum predicted return to open a new position,
`exitThreshold` the maximum predicted return to close an existing position, and
`maxPositions` is the maximum number of open positions. Everyday, this metrics
adjusts its portfolio based on the stock algorithm's prediction `Target`. For a
current position, if its predicted return is lower than `exitThreshold`, then
metrics will close this positions. On the other hand, metrics will look at all
stocks that have predicted return higher than `enterThreshold`, and will
repeatedly open new ones with maximum predicted value until it reaches
`maxPositions`.

##### First Evaluation: Demo1
[stock.Demo1](Demo1.scala) shows a sample Runner program. To run this
evaluation, you have to specify two sets of parameters:

1. `DataSourceParams` governs the ticker and the time window you wish to
   evaluate. Here is the parameter we used in stock.Demo1.
   ```scala
   val dataSourceParams = new DataSourceParams(
     baseDate = new DateTime(2004, 1, 1, 0, 0),
     fromIdx = 400,
     untilIdx = 1000,
     trainingWindowSize = 300,
     evaluationInterval = 20,
     marketTicker = "SPY",
     tickerList = Seq("AAPL"))
   ```
   This means we start our evaluation at 400 market days after the first day of
   2004 until 1200 days. Our `evalutionInterval` is 20 and `trainingWindowSize`
   is 300, meaning that we use day 100 until day 400 as *the first slice* of
   training data, and evaluate with data from day 400 until 420. Then, this
   process repeats, day 120 until 420 for training, and evaluation with data
   from day 420 until 440, until it reaches `untilIdx`. We only specify one
   ticker GOOGL for now, but our stock engine actually supports multiple
   tickers.

2. `BacktestingParams` governs the backtesting evaluation.
   ```scala
   val backtestingParams = BacktestingParams(enterThreshold = 0.001,
                                             exitThreshold = 0.0)
   ```
   As explained above, the backtesting evaluator opens a new long position when
   the prediction of stock is higher than 0.001, and will exit such position
   when the prediction is lower than 0.0.

You can run the evaluation with the following command.
```
$ cd $PIO_HOME/examples
$ ../bin/pio-run org.apache.predictionio.examples.stock.Demo1
```

You should see that we are trading from April 2005 until Dec 2007, the NAV went
from $1,000,000 to $1,433,449.24. YaY!!!

(**Disclaimer**: I cherrypicked this parameter to make the demo looks nice. A
buy-and-hold strategy of AAPL from April 2005 until 2007 performs even better.
And, you will lose money if you let it run for longer: set `untilIdx = 1400`.)

##### Second Evaluation
[stock.Demo2](Demo2.scala) shows us how to run backtesting against a basket of
stocks. You simply specify a list of tickers in the tickerList. `DataSource` wil
l handle cases where the ticker was absent.

In `BacktestingParams`, you may allow more stocks to be held concurrently. The
backtesting class essentially divides the current NAV by the `maxPositions`. The
demo is run the same way, by specifying the running main class.
```
$ cd $PIO_HOME/examples
$ ../bin/pio-run org.apache.predictionio.examples.stock.Demo2
```

The result is not as great, of course.

##### Third Evaluation
Now, you may start wondering what actually contribute to the profit, and may
want to dive deeper into the data. [DailyMetrics](DailyMetrics.scala) is a
helper metrics which helps you to understand the performance of different
parameter settings. It aggregates the prediction results for different
`enterThreshold`, and output the average / stdev return of the prediction
algorithm.

All you need is to change the `metrics` variable to `DailyMetrics`.
[Demo3](Demo3.scala) shows the actual code. Try it out with:
```
$ cd $PIO_HOME/examples
$ ../bin/pio-run org.apache.predictionio.examples.stock.Demo3
```

### Last Words
The current version is only a proof-of-concept for a stock engine using
PredictionIO infrastructure. *A lot of* possible improvements can be done:
- Use spark broadcast variable to deliver the whole dataset, instead of
  repeatedly copy to each Query and TrainingData. StockEngine should wrap around
  the data object and provide the correct slice of data and prevent look-ahead
  bias.
- Better backtesting metrics report. Should also calculate various important
  metrics like daily / annual return and volatility, sharpe and other metrics,
  drawdown, etc.
- Better backtesting method. Should be able to handle cost of capital, margin,
  transaction costs, variable position sizes, etc.
- And a lot more.....


