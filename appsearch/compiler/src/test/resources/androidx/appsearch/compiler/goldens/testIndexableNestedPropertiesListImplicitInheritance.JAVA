package com.example.appsearch;

import androidx.appsearch.app.AppSearchSchema;
import androidx.appsearch.app.DocumentClassFactory;
import androidx.appsearch.app.GenericDocument;
import androidx.appsearch.exceptions.AppSearchException;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.processing.Generated;

@Generated("androidx.appsearch.compiler.AppSearchCompiler")
public final class $$__AppSearch__Artist implements DocumentClassFactory<Artist> {
  public static final String SCHEMA_NAME = "Artist";

  @Override
  public String getSchemaName() {
    return SCHEMA_NAME;
  }

  @Override
  public AppSearchSchema getSchema() throws AppSearchException {
    return new AppSearchSchema.Builder(SCHEMA_NAME)
          .addParentType($$__AppSearch__Person.SCHEMA_NAME)
          .addProperty(new AppSearchSchema.StringPropertyConfig.Builder("name")
            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
            .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_NONE)
            .setIndexingType(AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_NONE)
            .setJoinableValueType(AppSearchSchema.StringPropertyConfig.JOINABLE_VALUE_TYPE_NONE)
            .build())
          .addProperty(new AppSearchSchema.DocumentPropertyConfig.Builder("livesAt", $$__AppSearch__Address.SCHEMA_NAME)
            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
            .setShouldIndexNestedProperties(false)
            .addIndexableNestedProperties("streetNumber")
            .addIndexableNestedProperties("streetName")
            .build())
          .addProperty(new AppSearchSchema.StringPropertyConfig.Builder("mostFamousWork")
            .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
            .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_NONE)
            .setIndexingType(AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_NONE)
            .setJoinableValueType(AppSearchSchema.StringPropertyConfig.JOINABLE_VALUE_TYPE_NONE)
            .build())
          .build();
  }

  @Override
  public List<Class<?>> getDependencyDocumentClasses() throws AppSearchException {
    List<Class<?>> classSet = new ArrayList<Class<?>>();
    classSet.add(Person.class);
    classSet.add(Address.class);
    return classSet;
  }

  @Override
  public GenericDocument toGenericDocument(Artist document) throws AppSearchException {
    GenericDocument.Builder<?> builder =
        new GenericDocument.Builder<>(document.namespace, document.id, SCHEMA_NAME);
    String nameCopy = document.name;
    if (nameCopy != null) {
      builder.setPropertyString("name", nameCopy);
    }
    Address livesAtCopy = document.livesAt;
    if (livesAtCopy != null) {
      GenericDocument livesAtConv = GenericDocument.fromDocumentClass(livesAtCopy);
      builder.setPropertyDocument("livesAt", livesAtConv);
    }
    String mostFamousWorkCopy = document.mostFamousWork;
    if (mostFamousWorkCopy != null) {
      builder.setPropertyString("mostFamousWork", mostFamousWorkCopy);
    }
    return builder.build();
  }

  @Override
  public Artist fromGenericDocument(GenericDocument genericDoc,
      Map<String, List<String>> documentClassMap) throws AppSearchException {
    String namespaceConv = genericDoc.getNamespace();
    String idConv = genericDoc.getId();
    String[] nameCopy = genericDoc.getPropertyStringArray("name");
    String nameConv = null;
    if (nameCopy != null && nameCopy.length != 0) {
      nameConv = nameCopy[0];
    }
    GenericDocument livesAtCopy = genericDoc.getPropertyDocument("livesAt");
    Address livesAtConv = null;
    if (livesAtCopy != null) {
      livesAtConv = livesAtCopy.toDocumentClass(Address.class, documentClassMap);
    }
    String[] mostFamousWorkCopy = genericDoc.getPropertyStringArray("mostFamousWork");
    String mostFamousWorkConv = null;
    if (mostFamousWorkCopy != null && mostFamousWorkCopy.length != 0) {
      mostFamousWorkConv = mostFamousWorkCopy[0];
    }
    Artist document = new Artist();
    document.namespace = namespaceConv;
    document.id = idConv;
    document.name = nameConv;
    document.livesAt = livesAtConv;
    document.mostFamousWork = mostFamousWorkConv;
    return document;
  }
}
