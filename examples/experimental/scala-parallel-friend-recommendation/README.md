Parallel SimRank Algorithm
========================================================================
Uses the Delta-Simrank Algorithm (http://dprg.cs.uiuc.edu/docs/deltasimrank/simrank.pdf).

Takes advantage of Spark's GraphX framework and in-memory/iterative computation advantage over other MapReduce frameworks such as YARN by performing each SimRank iteration as a triple of Map and two Reduce tasks.

Map1 : Emit key value pairs where each key is a pair of vertex ids adjacent to the vertices with updated scores in the previous iterations, and the value is the marginal increase in SimRank (refer to the delta-simrank algorithm for exact calculation method for deltas).

Reduce1 : Aggregate delta values by key.

Reduce2 : Add the delta to the previous iteration SimRank score to get the current iteration's SimRank score.

Prerequisite: GraphX package.

Parameter Explained
-------------------
datasource - graphEdgelistPath : The edge-list passed to GraphX's graph loader. For efficient memory storage of intermediate SimRank score calculations, the vertex ids should be in a contiguous range from 0 to (#Vertex-1). There is a utility function for re-mapping the vertex Id values : org.apache.predictionio.examples.pfriendrecommendation.DeltaSimRankRDD.normalizeGraph. 

The provided DataSource class uses the GraphLoader provided by GraphX. Graphs can be specified by a tab-separated edge list, where each line specifies one edge.
The the user can refer to the provided example edge list at `$EXAMPLE_HOME/data/edge_list_small.txt` for a graph specification with 10 vertices and 20 edges.

algorithms - numIterations : Number of iterations for calculating SimRank. Typical recommended is 6-8 in various papers (e.g. http://www-cs-students.stanford.edu/~glenj/simrank.pdf)

algorithms - decay : Decay constant used in calculating incremental changes to SimRank

### Using SimRank to Recommend Similar Items
SimRank's intuition says two items are similar if they have similar neighbor items. In a Facebook friend recommendation scenario, neighbors to a person can be that person's friends. Two people in the same circle of friends will have higher SimRank compared to two people in different friend circles.
For example, SimRank(Obama, George Bush) is high since they have many mutual president friends and belong to shared friend networks, whereas SimRank(Obama, Usher) is low since they have few mutual friends or friend networks.

### Configurable Datasources for Sampling
Three data sources can be configured from the engine factory : 

**DataSource** generates a GraphX graph using the entire dataset.

**NodeSamplingDataSource** generates a GraphX graph after performing node sampling
with induced edges between the sampled nodes. This data source takes an
additional parameter, sampleFraction, which is the fraction of graph nodes to
sample.

**ForestFireSamplingDataSource** generates a graph after performing forest fire
sampling. This sampling method also uses the sampleFraction parameter and takes
an additional parameter, geoParam, which is the parameter for a geometric
distribution that is used within forest fire sampling.

### Example query 
curl -H "Content-Type: application/json" -d '{"item1":10, "item2":9}' http://localhost:8000/queries.json

This queries the SimRank score between nodes with ids 10 and 9.
