package com.rackspace.papi.components.translation.xslt.xmlfilterchain;

import com.rackspace.papi.components.translation.xslt.AbstractXsltChain;
import com.rackspace.papi.components.translation.xslt.Parameter;
import com.rackspace.papi.components.translation.xslt.TransformReference;
import com.rackspace.papi.components.translation.xslt.XsltException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.xml.transform.sax.SAXTransformerFactory;
import org.xml.sax.XMLFilter;

public class XsltFilterChain extends AbstractXsltChain<XMLFilter> {
   
   public XsltFilterChain(SAXTransformerFactory factory) {
      super(factory, new ArrayList<TransformReference<XMLFilter>>());
   }
   
   public XsltFilterChain(SAXTransformerFactory factory, List<TransformReference<XMLFilter>> filters) {
       super(factory, filters);
   }
   
    @Override
   public void executeChain(InputStream in, OutputStream output, List<Parameter> inputs, List<Parameter<? extends OutputStream>> outputs) throws XsltException {
      new XsltFilterChainExecutor(this).executeChain(in, output, inputs, outputs);
   }
   
}