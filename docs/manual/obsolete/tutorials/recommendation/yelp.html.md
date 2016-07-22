---
title: Build a Sample Rails Application with Yelp Data
---
# Introduction

WARNING: This doc is applicable to 0.8.0 only. Updated version for 0.8.2 will be available soon.

In this tutorial we are going to create a business recommendation app using
item [recommendation engine](/engines/itemrec/)
and data set from [Yelp](https://www.kaggle.com/c/yelp-recsys-2013/data).

The Yelp [data set](https://www.kaggle.com/c/yelp-recsys-2013/data) contains data on 43k users,
12k business, and 230k reviews using a five star system. 

At the end you will have a fully functional app that shows the top five recommended business for
each user based on the businesses they has rated in the past. 

## Guide Assumptions

This is a beginner guide however it assumes you have already installed
[PredictionIO](/install) and that you have a basic familiarity with
[Rails](http://rubyonrails.org/). Some other prerequisites that will be needed include:

* [Ruby 2.1](https://www.ruby-lang.org) installed (earlier versions will probably work as well)
* [Rails 4.1](http://rubygems.org/gems/rails) installed
* [Postgres](http://www.postgresql.org/) or equivalent database

# Setting Up the Application

We are going to setup a basic Rails application.
**Experts** feel free to [skip ahead](#predictionio-setup) to the good stuff. 

First we will create the sample application.

```$ rails new predictionio_rails```

## Setup Database ##

You will need a database setup for this application.
Make the appropriate changes to your `Gemfile` and `config/database.yml` files. 
In our case we are going to use Postgres but any database should work.

At this point your Rails app should work. `$ rails s` and open [http://localhost:3000](http://localhost:3000/)
in your browser: You should see the standard Rails welcome message.

![Rails is Working](/images/tutorials/rails/rails-is-working.png)

## Importing Data Into the Application Database

We will be importing the users, business, and reviews into Postgres.

Later in the tutorial on we will loop through each record in the database and send it to PredicionIO Event Server.

Importing all this data can take some time and requires a fair bit of code. Look though the
code on in `lib/tasks/import/*.rake` on [GitHub](https://github.com/ramaboo/predictionio_rails/tree/master/lib/tasks/import)
if you are interested in the exact process otherwise 

You can also just [download](https://s3.amazonaws.com/predictionio-david/predictionio_rails.dump) a Postgres dump and import
it into your database.

# PredictionIO Setup

This guide assumes you have set `$PIO_HOME` to the path of the PredictionIO.
If you have not already done this you can do so with the following command:

```
$ export PIO_HOME=/path/to/your/PredictionIO-0.8.0
```

## Add PredictionIO Gem

To easily communicate with PredictionIO we will use the official
[PredictionIO gem](https://github.com/PredictionIO/PredictionIO-Ruby-SDK).

Add the following to your Gemfile:

```gem 'predictionio'```

and run `$ bundle install`.

## Checking PredictionIO

**Before continuing you want to check that PredictionIO is running correctly.**

You can use the [ps](http://en.wikipedia.org/wiki/Ps_(Unix)) command
and [grep](http://en.wikipedia.org/wiki/Grep) to check that each component is currently running.

PredictionIO requires the following components running:

* PredictionIO [Event Server](}/eventapi.html)
* [Elasticsearch](http://www.elasticsearch.org/)
* [HBase](http://hbase.apache.org/)

To check if the Event Server is running you can
type `$ ps aux | grep eventserver` which should output something like this:

![Grep Results](/images/tutorials/rails/grep-eventserver.png)

For Elasticsearch use `$ ps aux | grep elasticsearch`. For HBase use `$ ps aux | grep hbase`.

For additional help read [installing PredictionIO](/install/).

## Creating the Engine

An engine represents a type of prediction. For our purposes we will be using the
[item recommendation engine](/engines/itemrec/).

```
$ $PIO_HOME/bin/pio instance org.apache.predictionio.engines.itemrec
$ cd org.apache.predictionio.engines.itemrec
$ $PIO_HOME/bin/pio register
```

Which should output something like this: 

![PIO Register](/images/tutorials/rails/pio-register.png)

## Specify the Target App

Inside the engine instance folder edit
`params/datasource.json` and change the value of `appId` to fit your app - in our case 1.

The `appId` is a **numeric** ID that uniquely identifies your application.

![Params Datasource](/images/tutorials/rails/params-datasource.png)

We will also need to configure the engine to recognize our ranking scores as numeric values.
Edit `params/algorithms.json` and change `booleanData` to false.

![Params Algorithms](/images/tutorials/rails/params-algorithms.png)

## Sending Data to PredictionIO

We will be using a rake task `lib/tasks/import/predictionio.rake`
([show source](https://github.com/ramaboo/predictionio_rails/blob/master/lib/tasks/import/predictionio.rake)).

Run with `$ rake import:predictionio`.

## Train the Engine Instance

Before you can deploy your engine you need to train it. 

First you need to **change into the engine instance folder:**

```
$ cd $PIO_HOME/org.apache.predictionio.engines.itemrec
```

Train the engine with the imported data:

```
$ $PIO_HOME/bin/pio train
```

If it works you should see output that looks like this:

![PIO Train](/images/tutorials/rails/pio-train.png)

Each training command will produce an unique engine instance. 

## Launch the Engine Instance

Now it is time to launch the engine.

First you need to **change into the engine instance folder:**

```
$ cd $PIO_HOME/org.apache.predictionio.engines.itemrec
```

Then you can deploy with:

```
$ $PIO_HOME/bin/pio deploy
```

This will deploy the **most recent** engine instance.

You should see output that looks like this:

![PIO Deploy](/images/tutorials/rails/pio-deploy.png)

To check the engine is running correctly open a browser and navigate to [http://localhost:8000](http://localhost:8000/).

You should see something like this:

![localhost:8000](/images/tutorials/rails/localhost-8000.png)

If not check out the [engine documentation](/engines/) for additional troubleshooting options.

You can also run a quick query to double check that everything is good to go:

```
$ curl -i -X POST -d '{"uid": "261", "n": 5}' http://localhost:8000/queries.json
```

If you imported the data into a clean database then user 261 should have plenty
of entries to return a response that looks like this:

![Curl Output](/images/tutorials/rails/curl-261.png)

If you need to **delete all** your data you can do so with the following command:

```
$ curl -i -X DELETE http://localhost:7070/events.json?appId=<your_appId>
```

You will have to train and deploy again after this as well to completely remove everything!

## Model Retraining

In a production environment you will want to periodically re-train and re-deploy your model.

With Linux the easiest way to accomplish this is with [Cron](http://en.wikipedia.org/wiki/Cron) though any scheduler will work.

For our example we could train and deploy every 6 hours with the following:

```
$ crontab -e

0 */6 * * *     cd $PIO_HOME/org.apache.predictionio.engines.itemrec; $PIO_HOME/bin/pio train; $PIO_HOME/bin/pio deploy
```

It is not necessary to undeploy, the deploy command will do that automatically.

## Application Scaffolding

Now that our data is in PredictionIO it's time to build out our application a little.

First we are going to add a [counter cache column](http://guides.rubyonrails.org/association_basics.html#counter-cache)
to the users table so we can query users with lots of reviews.

You will need to change one line on `app/models/review.rb` to this:

```
belongs_to :user, counter_cache: true
```

And create a migration that updates each users counts ([show source](https://github.com/ramaboo/predictionio_rails/blob/master/db/migrate/20141008013504_add_counter_cache_to_users.rb)).

```
$ rails g migration add_counter_cache_to_users reviews_count:integer

```

and then `$ rake db:migrate`. Because we loop through the entire users table with this migration
it may take 20 minutes or more to run the [downloadable](https://s3.amazonaws.com/predictionio-david/predictionio_rails.dump) database dump already includes this migration.


Next create a simple controller at `app/controllers/users_controller.rb` with an index action.

```
class UsersController < ApplicationController
  def index
    @users = User.order('reviews_count DESC').limit(20)
  end
end
```

And setup routes in `config/routes.rb`.

```
resources :users, only: [:index, :show]
root 'users#index'
```

Next we will create a basic view at `app/views/users/index.html.erb`
([view source](https://github.com/ramaboo/predictionio_rails/blob/master/app/views/users/index.html.erb)).

As well as a few other cosmetic changes to `app/views/layouts/application.html.erb`
([view source](https://github.com/ramaboo/predictionio_rails/blob/master/app/views/layouts/application.html.erb)).

At this point if you open a browser and navigate to [http://localhost:3000](http://localhost:3000/) you should see this:

![Users Index](/images/tutorials/rails/users-index.png)

## Querying PredictionIO

Now the fun stuff. We are going to query PredictionIO for business recommendations based on the users rating history.

For our user page we want to display three things

* Basic information about the user.
* A list of 10 recent reviews by the user.
* A list of 5 **recommended** business for the user based on PredictionIO.

First lets create a show action in `app/controllers/users_controller.rb`
([view source](https://github.com/ramaboo/predictionio_rails/blob/master/app/controllers/users_controller.rb)).

The first part of the code finds the correct user object. Then we find some
recent reviews for that user and finally we query PredictionIO for recommended businesses.
With the query results we then need to loop through them and load our respective businesses.

```
def show
  # Find the correct user.
  @user = User.find(params[:id])

  # Find 10 recent reviews by the user. We use eager loading here to reduce database queries.
  @recent_reviews = @user.reviews.includes(:business).order('created_at DESC').limit(10)

  # Create new PredictionIO client.
  client = PredictionIO::EngineClient.new

  # Query PredictionIO for 5 recommendations!
  object = client.send_query('uid' => @user.id, 'n' => 5)

  # Initialize empty recommendations array.
  @recommendations = []

  # Loop though item recommendations returned from PredictionIO.
  object['items'].each do |item|
    # Initialize empty recommendation hash.
    recommendation = {}

    # Each item hash has only one key value pair so the first key is the item ID (in our case the business ID).
    business_id = item.keys.first

    # Find the business.
    business = Business.find(business_id)
    recommendation[:business] = business

    # The value of the hash is the predicted preference score.
    score = item.values.first
    recommendation[:score] = score

    # Add to the array of recommendations.
    @recommendations << recommendation
  end
end
```

And finally we create view at `app/views/users/show.html.erb`
([view source](https://github.com/ramaboo/predictionio_rails/blob/master/app/views/users/show.html.erb)) to display the information
to the user with a little styling help from [Bootstrap](http://getbootstrap.com/).

At this point if you open a browser and navigate to
[http://localhost:3000/users/6279](http://localhost:3000/users/6279) you should see something like this:

![Users Page](/images/tutorials/rails/users-show.png)

# Conclusion

Now you have learned how to use PredictionIO to offer recommendations to
users based on previous actions. Personalized recommendations can be a great addition to any application.
Next, you can try [adding additional engines](/tutorials/enginebuilders/local-helloworld.html) or
play with different input features to see how the results change. 

If you have any questions about PredictionIO check out our [documentation](http://docs.prediction.io/) and feel
free to ask for help in our [Google Group](http://groups.google.com/group/predictionio-dev).

As always the code in this tutorial is open source (MIT) so feel free to
[fork](https://github.com/ramaboo/predictionio_rails/) it on GitHub.
Let us know [@PredictionIO](http://twitter.com/PredictionIO) if you create something cool with it.

