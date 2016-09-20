package com.aerospike.spark.sql

import scala.collection.mutable.WrappedArray

import org.apache.spark.sql.Row
import org.apache.spark.sql.types.ArrayType
import org.apache.spark.sql.types.BinaryType
import org.apache.spark.sql.types.DataType
import org.apache.spark.sql.types.DateType
import org.apache.spark.sql.types.DoubleType
import org.apache.spark.sql.types.FloatType
import org.apache.spark.sql.types.IntegerType
import org.apache.spark.sql.types.LongType
import org.apache.spark.sql.types.MapType
import org.apache.spark.sql.types.NullType
import org.apache.spark.sql.types.ShortType
import org.apache.spark.sql.types.StringType
import org.apache.spark.sql.types.StructField
import org.apache.spark.sql.types.StructType
import collection.JavaConverters._

import com.aerospike.client.Bin


/**
  * This object provides utility methods to convert between
  * the Aerospike and park SQL types
  */
object TypeConverter{

  def binNamesOnly(fieldNames:Array[String], metaFields:Set[String]):Array[String] = {
    val binsOnly = fieldNames.toSet.diff(metaFields).toSeq.sortWith(_ < _)
    binsOnly.toArray
  }

  def metaFields(aerospikeConfig: AerospikeConfig): Set[String] = {
    Set(aerospikeConfig.keyColumn(),
      aerospikeConfig.digestColumn(),
      aerospikeConfig.expiryColumn(),
      aerospikeConfig.generationColumn(),
      aerospikeConfig.ttlColumn())
  }

  def binToValue(schema: StructType, bin: (String, Object)): Any = {

    val binVal = bin._2
    val binName = bin._1
    //println(s"binToValue - name:$binName = value:$binVal")

    val value = schema(binName).dataType match {
      case _: LongType => if (binVal == null) null else binVal.asInstanceOf[java.lang.Number].longValue
      case _: IntegerType => if (binVal == null) null else binVal.asInstanceOf[java.lang.Number].intValue
      case _: DoubleType => if (binVal == null) null else binVal.asInstanceOf[java.lang.Number].doubleValue()
      case _: ArrayType => {
        if (binVal == null) null else
          //println(binVal.getClass.getCanonicalName)
          binVal
      }
      case _: MapType => if (binVal == null) null else binVal.asInstanceOf[java.util.Map[Any,Any]].asScala
      case null => null
      case _ => if (binVal == null) null else binVal.toString()
    }
    value
  }

  def fieldToBin(schema: StructType, row:Row, field:String): Bin = {

    val value = row(schema.fieldIndex(field))
    if (value == null) {
      Bin.asNull(field)
    } else {
      schema(field).dataType match {
        case StringType => new Bin(field, value)
        case LongType => new Bin(field, value.asInstanceOf[java.lang.Long])
        case IntegerType => new Bin(field, new java.lang.Long(value.asInstanceOf[Int]))
        case ShortType => new Bin(field, value.asInstanceOf[java.lang.Long])
        case DoubleType => new Bin(field, value.asInstanceOf[java.lang.Double])
        case FloatType => new Bin(field, value.asInstanceOf[java.lang.Double])
        case DateType => new Bin(field, value.asInstanceOf[java.sql.Date].getTime)
        case NullType => Bin.asNull(field)
        case _: ArrayType => {
          value match {
            case _: WrappedArray[Any] => new Bin(field, value.asInstanceOf[WrappedArray[Any]].toList.asJava)
            case _: Seq[Any] => new Bin(field, scala.collection.JavaConversions.seqAsJavaList(value.asInstanceOf[Seq[Any]]))
            case _: List[Any] => new Bin(field, value)
          }
        }
        case _: MapType => {
          value match {
            case _: scala.collection.Map[Any, Any] => new Bin(field, value.asInstanceOf[scala.collection.Map[Any, Any]].asJava)
          }
        }
        case null => Bin.asNull(field)
        case _ => new Bin(field, value.toString())
      }
    }
  }

  def valueToSchema(bin: (String, Object)): StructField = {

    val binVal = bin._2
    val binName = bin._1
    val field = binVal match {
      case _: java.lang.Integer => StructField(binName, LongType, nullable = true)
      case _: java.lang.Short => StructField(binName, LongType, nullable = true)
      case _: java.lang.Long => StructField(binName, LongType, nullable = true)
      case _: java.lang.Double => StructField(binName, DoubleType, nullable = true)
      case _: java.lang.Float => StructField(binName, DoubleType, nullable = true)
      case s: String => StructField(binName, StringType, nullable = true)
      case _: java.util.Map[Object,Object] => {
        val aKey = valueToSchema((binName, binVal.asInstanceOf[java.util.Map[Object, Object]].asScala.keys.head))
        val aValue = valueToSchema((binName, binVal.asInstanceOf[java.util.Map[Object, Object]].asScala.values.head))
        StructField(binName, new MapType(aKey.dataType, aValue.dataType, true), nullable = true)
      }
      case _: java.util.List[Object] => {
        val newValue = binVal.asInstanceOf[java.util.List[Object]].get(0)
        val elementStructure = valueToSchema((binName, newValue))
        StructField(binName, new ArrayType(elementStructure.dataType , true))
      }
      //case ParticleType.GEOJSON => StructField(binName, StringType, nullable = true) //TODO
      case Array => StructField(binName, BinaryType, nullable = true)
      case _ => StructField(binName, BinaryType, nullable = true)
    }
    field
  }

}
