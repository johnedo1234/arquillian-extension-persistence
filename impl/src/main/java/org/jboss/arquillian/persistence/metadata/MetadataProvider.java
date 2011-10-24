/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,  
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.persistence.metadata;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jboss.arquillian.persistence.Data;
import org.jboss.arquillian.persistence.DataSource;
import org.jboss.arquillian.persistence.Expected;
import org.jboss.arquillian.persistence.TransactionMode;
import org.jboss.arquillian.persistence.Transactional;
import org.jboss.arquillian.persistence.configuration.PersistenceConfiguration;
import org.jboss.arquillian.persistence.data.DataSetDescriptor;
import org.jboss.arquillian.persistence.data.DataSetFileNamingStrategy;
import org.jboss.arquillian.persistence.data.ExpectedDataSetFileNamingStrategy;
import org.jboss.arquillian.persistence.data.Format;
import org.jboss.arquillian.persistence.exception.DataSourceNotDefinedException;
import org.jboss.arquillian.persistence.exception.UnsupportedDataFormatException;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.arquillian.test.spi.event.suite.TestEvent;

public class MetadataProvider
{

   private final PersistenceConfiguration configuration;

   private final MetadataExtractor metadataExtractor;

   private final TestClass testClass;

   private final Method testMethod;

   public MetadataProvider(TestClass testClass, Method testMethod, PersistenceConfiguration configuration)
   {
      this.metadataExtractor = new MetadataExtractor(testClass, testMethod);
      this.configuration = configuration;
      this.testClass = testClass;
      this.testMethod = testMethod;
   }

   public MetadataProvider(TestEvent testEvent, PersistenceConfiguration configuration)
   {
      this(testEvent.getTestClass(), testEvent.getTestMethod(), configuration);
   }
   
   // ---------------------------------------------------------------------------------------------------
   // Public API methods
   // ---------------------------------------------------------------------------------------------------
   
   public boolean isPersistenceFeatureEnabled()
   {
      if (!hasDataSourceAnnotation() && !configuration.isDefaultDataSourceDefined())
      {
         throw new DataSourceNotDefinedException("Data source not defined!");
      }

      return (hasDataAnnotation() || hasExpectedAnnotation() || hasPersistenceTestAnnotation());
   }
   
   public boolean isDataVerificationExpected()
   {
      return metadataExtractor.hasExpectedAnnotationOn(AnnotationLevel.CLASS)
            || metadataExtractor.hasExpectedAnnotationOn(AnnotationLevel.METHOD);
   }
   
   public boolean isTransactional()
   {
      boolean transactionalSupportDefinitionOnClassLevel = metadataExtractor.hasTransactionalSupportEnabledOn(AnnotationLevel.CLASS);
      if (transactionalSupportDefinitionOnClassLevel)
      {
         return metadataExtractor.hasTransactionalSupportEnabledOn(AnnotationLevel.METHOD);
      }
      return transactionalSupportDefinitionOnClassLevel;  
   }
   
   public TransactionMode getTransactionalMode()
   {
      Transactional transactionalAnnotation = metadataExtractor.getTransactionalAnnotationOn(AnnotationLevel.CLASS);
      if (metadataExtractor.hasTransactionalAnnotationOn(AnnotationLevel.METHOD))
      {
         transactionalAnnotation = metadataExtractor.getTransactionalAnnotationOn(AnnotationLevel.METHOD);
      }
      
      TransactionMode mode = configuration.getDefaultTransactionMode();
      if (transactionalAnnotation != null)
      {
         mode = transactionalAnnotation.value();
      }
      return mode;
   }

   public String getDataSourceName()
   {
      String dataSource = "";

      if (configuration.isDefaultDataSourceDefined())
      {
         dataSource = configuration.getDefaultDataSource();
      }

      if (hasDataSourceAnnotation())
      {
         dataSource = getDataSourceAnnotation().value();
      }

      if ("".equals(dataSource.trim()))
      {
         throw new DataSourceNotDefinedException("DataSource not defined");
      }

      return dataSource;
   }
   
   public List<DataSetDescriptor> getDataSetDescriptors()
   {
      final List<DataSetDescriptor> dataSetDescriptors = new ArrayList<DataSetDescriptor>();
      for (String dataFileName : getDataFileNames())
      {
         DataSetDescriptor dataSetDescriptor = new DataSetDescriptor(dataFileName, inferFormat(dataFileName));
         dataSetDescriptors.add(dataSetDescriptor);
      }
      
      return dataSetDescriptors;
   }
   
   public List<DataSetDescriptor> getExpectedDataSetDescriptors()
   {
      final List<DataSetDescriptor> dataSetDescriptors = new ArrayList<DataSetDescriptor>();
      for (String dataFileName : getExpectedDataFileNames())
      {
         DataSetDescriptor dataSetDescriptor = new DataSetDescriptor(dataFileName, inferFormat(dataFileName));
         dataSetDescriptors.add(dataSetDescriptor);
      }
      
      return dataSetDescriptors;
   }

   // ---------------------------------------------------------------------------------------------------
   // Internal methods
   // ---------------------------------------------------------------------------------------------------

   boolean hasDataAnnotation()
   {
      return metadataExtractor.hasDataAnnotationOn(AnnotationLevel.CLASS)
            || metadataExtractor.hasDataAnnotationOn(AnnotationLevel.METHOD);
   }
   
   private boolean hasExpectedAnnotation()
   {
      return metadataExtractor.hasExpectedAnnotationOn(AnnotationLevel.CLASS)
            || metadataExtractor.hasExpectedAnnotationOn(AnnotationLevel.METHOD);
   }
   
   private boolean hasPersistenceTestAnnotation()
   {
      return metadataExtractor.hasPersistenceTestAnnotation();
   }

   private boolean hasDataSourceAnnotation()
   {
      return metadataExtractor.hasDataSourceAnnotationOn(AnnotationLevel.CLASS)
            || metadataExtractor.hasDataSourceAnnotationOn(AnnotationLevel.METHOD);
   }

   private Data getDataAnnotation()
   {
      Data usedAnnotation = metadataExtractor.getDataAnnotationOn(AnnotationLevel.CLASS);
      if (metadataExtractor.hasDataAnnotationOn(AnnotationLevel.METHOD))
      {
         usedAnnotation = metadataExtractor.getDataAnnotationOn(AnnotationLevel.METHOD);
      }

      return usedAnnotation;
   }
   
   private DataSource getDataSourceAnnotation()
   {
      DataSource usedAnnotation = metadataExtractor.getDataSourceAnnotationOn(AnnotationLevel.CLASS);
      if (metadataExtractor.hasDataSourceAnnotationOn(AnnotationLevel.METHOD))
      {
         usedAnnotation = metadataExtractor.getDataSourceAnnotationOn(AnnotationLevel.METHOD);
      }

      return usedAnnotation;
   }

   private Expected getExpectedAnnotation()
   {
      Expected usedAnnotation = metadataExtractor.getExpectedAnnotationOn(AnnotationLevel.CLASS);
      if (metadataExtractor.hasExpectedAnnotationOn(AnnotationLevel.METHOD))
      {
         usedAnnotation = metadataExtractor.getExpectedAnnotationOn(AnnotationLevel.METHOD);
      }
      
      return usedAnnotation;
   }
   
   List<Format> getDataFormats()
   {
      final List<Format> formats = new ArrayList<Format>();
      for (String dataFileName : getDataFileNames())
      {
         formats.add(inferFormat(dataFileName));
      }
      return formats;
   }
   
   private Format inferFormat(String dataFileName)
   {
      Format format = Format.inferFromFile(dataFileName);
      if (Format.UNSUPPORTED.equals(format))
      {
         throw new UnsupportedDataFormatException("File " + getDataFileNames() + " is not supported.");
      }
      return format;
   }
   
   List<String> getDataFileNames()
   {
      Data dataAnnotation = getDataAnnotation();
      String[] specifiedFileNames = dataAnnotation.value();
      if (specifiedFileNames.length == 0 || "".equals(specifiedFileNames[0].trim()))
      {
         return Arrays.asList(getDefaultNamingForDataSetFile());
      }
      return Arrays.asList(specifiedFileNames);
   }
   
   List<Format> getExpectedDataFormats()
   {
      final List<Format> formats = new ArrayList<Format>();
      for (String dataFileName : getExpectedDataFileNames())
      {
         formats.add(inferFormat(dataFileName));
      }
      return formats;
   }

   List<String> getExpectedDataFileNames()
   {
      Expected expectedAnnotation = getExpectedAnnotation();
      String[] specifiedFileNames = expectedAnnotation.value();
      if (specifiedFileNames.length == 0 || "".equals(specifiedFileNames[0].trim()))
      {
         return Arrays.asList(getDefaultNamingForExpectedDataSetFile());
      }
      return Arrays.asList(specifiedFileNames);
   }
   
   private String getDefaultNamingForDataSetFile()
   {
      Format format = configuration.getDefaultDataSetFormat();
      
      if (metadataExtractor.hasDataAnnotationOn(AnnotationLevel.METHOD))
      {
         return new DataSetFileNamingStrategy(format).createFileName(testClass.getJavaClass(), testMethod);
      }
      
      return new DataSetFileNamingStrategy(format).createFileName(testClass.getJavaClass());
   }

   
   private String getDefaultNamingForExpectedDataSetFile()
   {
      Format format = configuration.getDefaultDataSetFormat();

      if (metadataExtractor.hasExpectedAnnotationOn(AnnotationLevel.METHOD))
      {
         return new ExpectedDataSetFileNamingStrategy(format).createFileName(testClass.getJavaClass(), testMethod);
      }

      return new ExpectedDataSetFileNamingStrategy(format).createFileName(testClass.getJavaClass());
   }

}
