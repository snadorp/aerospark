package com.aerospike.spark.sql

import scala.collection.JavaConversions._
import scala.util.parsing.json.JSONType

import org.apache.spark.Logging
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.sql.SQLContext
import org.apache.spark.sql.sources._
import org.apache.spark.sql.types.BinaryType
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.types.LongType
import org.apache.spark.sql.types.StringType
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.types.MapType

import com.aerospike.client.Value
import com.aerospike.client.command.ParticleType
import com.aerospike.helper.query.Qualifier
import com.aerospike.helper.query.Qualifier.FilterOperation
import com.aerospike.spark.sql.AerospikeConfig._
import com.aerospike.helper.query.KeyRecordIterator
import com.aerospike.client.query.KeyRecord
import com.aerospike.client.query.Statement
import com.aerospike.client.policy.QueryPolicy
import com.aerospike.client.AerospikeClient
import scala.collection.mutable.ListBuffer
import org.apache.spark.sql.types.IntegerType


class AerospikeRelation( config: AerospikeConfig, userSchema: StructType)
  (@transient val sqlContext: SQLContext) 
    extends BaseRelation
    with TableScan
    with PrunedFilteredScan 
    with Logging 
    with Serializable {

  val conf = config
  
  var schemaCache: StructType = null

  override def schema: StructType = {
    val SCAN_COUNT = config.scanCount
    
    if (schemaCache == null || schemaCache.isEmpty) {

      val client = AerospikeConnection.getClient(config) 
      
      var fields = collection.mutable.Map[String,StructField]()
  		fields += "key" -> StructField("key", StringType, true)
  		fields += "digest" -> StructField("digest", BinaryType, false)
//  		fields += "expiration" -> StructField("expiration", IntegerType, true)
//  		fields += "generation" -> StructField("generation", IntegerType, true)
     
//  		var stmt = new Statement();
//  		stmt.setNamespace(config.get(AerospikeConfig.NameSpace).asInstanceOf[String]);
//  		stmt.setSetName(config.get(AerospikeConfig.SetName).asInstanceOf[String]);
//  		var recordSet = client.query(null, stmt)
//  		    
//  		try{
//  		  val sample = recordSet.take(SCAN_COUNT)
//  			sample.foreach { keyRecord => 
//    
//        keyRecord.record.bins.foreach { bin =>
//          val binVal = bin._2
//          val binName = bin._1
//          val field = TypeConverter.valueToSchema(bin)
//          
//            fields.get(binName) match {
//              case Some(e) => fields.update(binName, field)
//              case None    => fields += binName -> field
//            }
//          }
//  			}
//  		} finally {
//  			recordSet.close();
//  		}
  		
  		val fieldSeq = fields.values.toSeq
  		schemaCache = StructType(fieldSeq)
  	} 
    schemaCache
  }
  
  override def buildScan(): RDD[Row] = {
    new KeyRecordRDD(sqlContext.sparkContext, conf, schemaCache)
  }
  
  
  override def buildScan(
    requiredColumns: Array[String],
    filters: Array[Filter]): RDD[Row] = {
      
    if (filters.length >0){
      val allFilters = filters.map { _ match {
        case EqualTo(attribute, value) =>
          new Qualifier(attribute, FilterOperation.EQ, Value.get(value))

        case GreaterThanOrEqual(attribute, value) =>
          new Qualifier(attribute, FilterOperation.GTEQ, Value.get(value))

        case GreaterThan(attribute, value) =>
          new Qualifier(attribute, FilterOperation.GT, Value.get(value))

        case LessThanOrEqual(attribute, value) =>
          new Qualifier(attribute, FilterOperation.LTEQ, Value.get(value))

        case LessThan(attribute, value) =>
          new Qualifier(attribute, FilterOperation.LT, Value.get(value))

        case _ => None
        }
      }.asInstanceOf[Array[Qualifier]]
      
      new KeyRecordRDD(sqlContext.sparkContext, conf, schemaCache, requiredColumns, allFilters)
    } else {
      new KeyRecordRDD(sqlContext.sparkContext, conf, schemaCache, requiredColumns)
    }
  }
  
  
}
