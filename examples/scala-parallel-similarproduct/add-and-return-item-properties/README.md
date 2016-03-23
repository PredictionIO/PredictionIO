---
#PredictionIO: Add Your Own Properties to Returned Items
---

This small how-to explains how to add user defined properties to items returned by PredictionIO engine.
This how-to is based on the [Similar Product Engine Template](http://docs.prediction.io/templates/similarproduct/quickstart/) version v0.3.2
To use this how-to you need to be familiar with scala programming language.
In this how-to we also suppose you was able to set up and run `Similar Product Engine` (see their [quick start guide](http://docs.prediction.io/templates/similarproduct/quickstart/)).

A full end-to-end example can be found on
[GitHub](https://github.com/PredictionIO/PredictionIO/tree/develop/examples/scala-parallel-similarproduct/add-and-return-item-properties).

## THE TASK

Suppose you would like to use [Similar Product Engine](http://docs.prediction.io/templates/similarproduct/quickstart/)
for suggesting your users the videos they can also like. The `Similar Product Engine` will answer to you
with list of IDs for such videos. So, for example `REST` response from the engine right now
looks like the one below
```json
{"itemScores":[
	{
		"item":"i12",
		"score":1.1700499715209998
	},{
		"item":"i44",
		"score":1.1153550716504106
	}
]}
```

But you want the engine to return more information about every video. Let's think you want add fields
`title`, `date`, and `imdbUrl` to every item, so, the resulting `REST` respose
for your case should look similar to the posted below
```json
{"itemScores":[
	{
		"item":"i12",
		"title":"title for movie i12",
		"date":"1935",
		"imdbUrl":"http://imdb.com/fake-url/i12",
		"score":1.1700499715209998
	},{
		"item":"i44",
		"title":"title for movie i44",
		"date":"1974",
		"imdbUrl":"http://imdb.com/fake-url/i44",
		"score":1.1153550716504106
	}
]}
```

## SO, HOW TO?

### The Main Idea

Recall [the DASE Architecture](http://docs.prediction.io/templates/similarproduct/dase/), a PredictionIO engine has
4 main components: `Data Source`, `Data Preparator`, `Algorithm`, and `Serving`
components. To achieve your goal, you will need provide the information about video to engine
(using sdk), and then let this information to pass from `Data Source` through all the engine
to the `Serving` component where the engine will send required information back to your application.

### Implementation

#### Modify The Item
In file [DataSource.scala#L99](https://github.com/PredictionIO/template-scala-parallel-similarproduct/blob/develop/src/main/scala/DataSource.scala#L99)
you will find class `Item` defined in the next way
```scala
case class Item(categories: Option[List[String]])
```

At the first, we need simply add required fields to this class
```scala
case class Item(
	title: String,
	date: String,
	imdbUrl: String,
	categories: Option[List[String]])
```

#### Create The Item Properly
Now, your IDE (or compiler) will say you about all the places where you need make changes to create item
properly. For example, [DataSource.scala#L51](https://github.com/PredictionIO/template-scala-parallel-similarproduct/blob/develop/src/main/scala/DataSource.scala#L51)
```scala
Item(categories = properties.getOpt[List[String]]("categories"))
```
You need now to add needed properties to item
```scala
Item(
	title = properties.get[String]("title"),
	date = properties.get[String]("date"),
	imdbUrl = properties.get[String]("imdbUrl"),
	categories = properties.getOpt[List[String]]("categories"))
```

#### Modify The ItemScore
Now, when you've fixed item creation, take a look on class `ItemScore` from the file [Engine.scala](https://github.com/PredictionIO/template-scala-parallel-similarproduct/blob/develop/src/main/scala/Engine.scala)
```scala
case class ItemScore(
	item: String,
	score: Double
) extends Serializable
```
Engine will return class `PredictedResult` which contains property `itemScores: Array[ItemScore]`.
So, since your result items are of class`ItemScore`, you need modify this class too.
In our example after modification you will have something similar to below
```scala
case class ItemScore(
	item: String,
	title: String,
	date: String,
	imdbUrl: String,
	score: Double
) extends Serializable
```

#### Create The ItemScore Properly

Again, now you need to go through all the places where `ItemScore` is created and fix compiler errors.

Result is initially created by the `Algorithm` component and then is passed to the `Serving` component.
Take a look on a place where object of class ItemScore is initially created in file [ALSAlgorithm.scala#L170](https://github.com/PredictionIO/template-scala-parallel-similarproduct/blob/develop/src/main/scala/ALSAlgorithm.scala#L170).
```scala
new ItemScore(
	item = model.itemIntStringMap(i),
	score = s
)
```
You code after changes will be similar to posted below
```scala
val it = model.items(i)
new ItemScore(
	item = model.itemIntStringMap(i),
	title = it.title,
	date = it.date,
	imdbUrl = it.imdbUrl,
	score = s
)
```
Using `model.items(i)` you can receive corresponding object of the `Item` class,
and now you can access its properties which you created during previous step.
Using `model.itemIntStringMap(i)` you can receive ID of corresponding item.

Since the Cooccurrence Algorithm was added in [v0.3.2](https://github.com/PredictionIO/template-scala-parallel-similarproduct#v031), you need to modify [CooccurrenceAlgorithm.scala#L126](https://github.com/PredictionIO/template-scala-parallel-similarproduct/blob/develop/src/main/scala/CooccurrenceAlgorithm.scala#L126) in order for the build to succeed. 
```scala
ItemScore(
	item = model.itemIntStringMap(index),
	score = count
)
```
You code after changes will be similar to posted below
```scala
val it = model.items(index)
ItemScore(
	item = model.itemIntStringMap(index),
	title = it.title,
	date = it.date,
	imdbUrl = it.imdbUrl,
	score = count
)
```
#### Modify Script That Supplies Data For The Engine
And this is the final step. You should supply your data to the engine using new format now.
To get the idea take a look on this piece of code in our sample python script in [data/import_eventserver.py](https://github.com/PredictionIO/template-scala-parallel-similarproduct/blob/develop/data/import_eventserver.py#L34)
that creates test.

Creating item before modification.
```python
client.create_event(
	event="$set",
	entity_type="item",
	entity_id=item_id,
	properties={
		"categories" : random.sample(categories, random.randint(1, 4))
	}
)
```
Creating item after modification.
```python
client.create_event(
	event="$set",
	entity_type="item",
	entity_id=item_id,
	properties={
		"categories" : random.sample(categories, random.randint(1, 4)),
		"title": "title for movie " + item_id,
		"date": 1935 + random.randint(1, 25),
		"imdbUrl": "http://imdb.com/fake-url/" + item_id
	}
)
```

#### Try It!
When you are ready, don't forget to fill application with new data and then
```bash
$ pio build
$ pio train
$ pio deploy
```

Now, you should be able to see desired results by querying engine
```bash
curl -H "Content-Type: application/json" -d '{ "items": ["i1", "i3"], "num": 10}' http://localhost:8000/queries.json
```

A full end-to-end example can be found on
[GitHub](https://github.com/PredictionIO/PredictionIO/tree/develop/examples/scala-parallel-similarproduct/add-and-return-item-properties).
