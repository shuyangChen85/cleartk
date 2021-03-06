/** 
 * Copyright (c) 2009, Regents of the University of Colorado 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer. 
 * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution. 
 * Neither the name of the University of Colorado at Boulder nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE. 
 */
package org.cleartk.token.pos;

import java.util.ArrayList;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Type;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.cleartk.ml.CleartkSequenceAnnotator;
import org.cleartk.ml.Feature;
import org.cleartk.ml.Instance;
import org.cleartk.util.ReflectionUtil;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.initializable.InitializableFactory;
import org.apache.uima.fit.util.JCasUtil;

/**
 * <br>
 * Copyright (c) 2009, Regents of the University of Colorado <br>
 * All rights reserved.
 * 
 * @author Philip Ogren
 * 
 */

public abstract class PosAnnotator<TOKEN_TYPE extends Annotation, SENTENCE_TYPE extends Annotation>
    extends CleartkSequenceAnnotator<String> {

  public static final String PARAM_FEATURE_EXTRACTOR_CLASS_NAME = "featureExtractorClassName";

  @ConfigurationParameter(
      name = PARAM_FEATURE_EXTRACTOR_CLASS_NAME,
      mandatory = true, description = "provides the full name of the class that will be used to extract features", defaultValue = "org.cleartk.token.pos.impl.DefaultFeatureExtractor")
  private String featureExtractorClassName;

  protected PosFeatureExtractor<TOKEN_TYPE, SENTENCE_TYPE> featureExtractor;

  private Class<? extends TOP> tokenClass;

  private Class<? extends TOP> sentenceClass;

  protected boolean typesInitialized = false;

  protected Type tokenType;

  protected Type sentenceType;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);

    // extract the token and sentence classes from the type parameters
    this.tokenClass = ReflectionUtil.<Class<? extends TOP>> uncheckedCast(ReflectionUtil
        .getTypeArgument(PosAnnotator.class, "TOKEN_TYPE", this));
    this.sentenceClass = ReflectionUtil.<Class<? extends TOP>> uncheckedCast(ReflectionUtil
        .getTypeArgument(PosAnnotator.class, "SENTENCE_TYPE", this));

    // create the feature extractor and tagger
    PosFeatureExtractor<?, ?> untypedExtractor = InitializableFactory.create(
        context,
        featureExtractorClassName,
        PosFeatureExtractor.class);

    // check that the type parameters are compatible
    ReflectionUtil.checkTypeParameterIsAssignable(
        PosFeatureExtractor.class,
        "TOKEN_TYPE",
        untypedExtractor,
        PosAnnotator.class,
        "TOKEN_TYPE",
        this);
    ReflectionUtil.checkTypeParameterIsAssignable(
        PosFeatureExtractor.class,
        "SENTENCE_TYPE",
        untypedExtractor,
        PosAnnotator.class,
        "SENTENCE_TYPE",
        this);

    // set the instance variables
    this.featureExtractor = ReflectionUtil.uncheckedCast(untypedExtractor);
  }

  protected void initializeTypes(JCas jCas) throws AnalysisEngineProcessException {
    try {
      tokenType = JCasUtil.getType(jCas, this.tokenClass);
      sentenceType = JCasUtil.getType(jCas, this.sentenceClass);
    } catch (Exception e) {
      throw new AnalysisEngineProcessException(e);
    }
    typesInitialized = true;
  }

  @Override
  public void process(JCas jCas) throws AnalysisEngineProcessException {
    if (!typesInitialized)
      initializeTypes(jCas);

    FSIterator<Annotation> sentences = jCas.getAnnotationIndex(sentenceType).iterator();
    while (sentences.hasNext()) {
      @SuppressWarnings("unchecked")
      SENTENCE_TYPE sentence = (SENTENCE_TYPE) sentences.next();

      List<Instance<String>> instances = new ArrayList<Instance<String>>();

      FSIterator<Annotation> tokens = jCas.getAnnotationIndex(tokenType).subiterator(sentence);

      while (tokens.hasNext()) {
        @SuppressWarnings("unchecked")
        TOKEN_TYPE token = (TOKEN_TYPE) tokens.next();
        List<Feature> features = featureExtractor.extractFeatures(jCas, token, sentence);
        Instance<String> instance = new Instance<String>();
        instance.addAll(features);
        instance.setOutcome(getTag(jCas, token));
        instances.add(instance);
      }

      if (this.isTraining()) {
        this.dataWriter.write(instances);
      } else {
        List<String> tags = this.classify(instances);
        tokens.moveToFirst();
        for (int i = 0; tokens.hasNext(); i++) {
          @SuppressWarnings("unchecked")
          TOKEN_TYPE token = (TOKEN_TYPE) tokens.next();
          setTag(jCas, token, tags.get(i));
        }
      }
    }
  }

  public abstract void setTag(JCas jCas, TOKEN_TYPE token, String tag);

  public abstract String getTag(JCas jCas, TOKEN_TYPE token);

  public void setFeatureExtractorClassName(String featureExtractorClassName) {
    this.featureExtractorClassName = featureExtractorClassName;
  }

}
