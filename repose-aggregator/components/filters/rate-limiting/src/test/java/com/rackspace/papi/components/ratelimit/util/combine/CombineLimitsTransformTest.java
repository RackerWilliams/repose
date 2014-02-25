package com.rackspace.papi.components.ratelimit.util.combine;

import com.rackspace.papi.commons.util.transform.StreamTransform;
import com.rackspace.papi.components.ratelimit.util.LimitsEntityStreamTransformer;
import com.rackspace.papi.components.ratelimit.util.TransformHelper;
import com.rackspace.repose.service.limits.schema.HttpMethod;
import com.rackspace.repose.service.limits.schema.ObjectFactory;
import com.rackspace.repose.service.limits.schema.RateLimitList;
import com.rackspace.repose.service.limits.schema.TimeUnit;
import com.rackspace.repose.service.ratelimit.RateLimitListBuilder;
import com.rackspace.repose.service.ratelimit.cache.CachedRateLimit;
import com.rackspace.repose.service.ratelimit.config.ConfiguredLimitGroup;
import com.rackspace.repose.service.ratelimit.config.ConfiguredRatelimit;
import org.junit.Before;
import org.junit.Test;

import javax.xml.bind.JAXBContext;
import java.io.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CombineLimitsTransformTest {

    public static final String SIMPLE_URI_REGEX = "/loadbalancer/.*", COMPLEX_URI_REGEX = "/loadbalancer/vips/.*";
    public static final String SIMPLE_URI = "*loadbalancer*", COMPLEX_URI = "*loadbalancer/vips*";
    public static final String SIMPLE_ID = "12345-ABCDE", COMPLEX_ID = "09876-ZYXWV";

    public static final String COMBINER_XSL_LOCATION = "/META-INF/xslt/limits-combine.xsl";
    public static final ObjectFactory LIMITS_OBJECT_FACTORY = new ObjectFactory();

    private final Pattern validationPattern = Pattern.compile(".*(<rates>.*</rates>).*(<absolute>.*</absolute>).*", Pattern.DOTALL);
    private StreamTransform<LimitsTransformPair, OutputStream> combiner;

    public static String readStream(String resourceLocation) throws Exception {
        final StringBuilder stringBuffer = new StringBuilder();

        final BufferedReader in = new BufferedReader(new InputStreamReader(
                CombineLimitsTransformTest.class.getResourceAsStream(resourceLocation)));

        String nextLine;

        while ((nextLine = in.readLine()) != null) {
            stringBuffer.append(nextLine);
        }

        return stringBuffer.toString();
    }

    @Before
    public void standUp() throws Exception {
        combiner = new CombinedLimitsTransformer(
                TransformHelper.getTemplatesFromInputStream(
                        LimitsEntityStreamTransformer.class.getResourceAsStream(COMBINER_XSL_LOCATION)),
                JAXBContext.newInstance(LIMITS_OBJECT_FACTORY.getClass()), LIMITS_OBJECT_FACTORY);
    }

    @Test
    public void shouldCombineInputStreamWithJaxbElement() throws Exception {
        final InputStream is = CombineLimitsTransformTest.class.getResourceAsStream(
                "/META-INF/schema/examples/absolute-limits.xml");

        RateLimitList rll = createRateLimitList();

        final LimitsTransformPair tPair = new LimitsTransformPair(is, rll);
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        combiner.transform(tPair, output);

        final String actual = output.toString();
        final Matcher matcher = validationPattern.matcher(actual);

        assertTrue("Combined limits must match expected output pattern", matcher.matches());

        assertNotNull("Combined limits must include rate limits", matcher.group(1));
        assertNotNull("Combined limits must include absolute limits", matcher.group(2));
    }

    private RateLimitList createRateLimitList() {
        final Map<String, CachedRateLimit> cacheMap;
        final ConfiguredLimitGroup configuredLimitGroup;

        LinkedList<HttpMethod> methods = new LinkedList<HttpMethod>();
        methods.add(HttpMethod.GET);
        methods.add(HttpMethod.PUT);
        methods.add(HttpMethod.POST);
        methods.add(HttpMethod.DELETE);

        cacheMap = new HashMap<String, CachedRateLimit>();
        configuredLimitGroup = new ConfiguredLimitGroup();

        configuredLimitGroup.setDefault(Boolean.TRUE);
        configuredLimitGroup.setId("configured-limit-group");
        configuredLimitGroup.getGroups().add("user");

        cacheMap.put(SIMPLE_ID, new CachedRateLimit(newLimitConfig(SIMPLE_ID, SIMPLE_URI, SIMPLE_URI_REGEX, methods), 1));

        configuredLimitGroup.getLimit().add(newLimitConfig(SIMPLE_ID, SIMPLE_URI, SIMPLE_URI_REGEX, methods));

        cacheMap.put(COMPLEX_ID, new CachedRateLimit(newLimitConfig(COMPLEX_ID, COMPLEX_URI, COMPLEX_URI_REGEX, methods), 1));

        configuredLimitGroup.getLimit().add(newLimitConfig(COMPLEX_ID, COMPLEX_URI, COMPLEX_URI_REGEX, methods));

        return new RateLimitListBuilder(cacheMap, configuredLimitGroup).toRateLimitList();
    }

    private ConfiguredRatelimit newLimitConfig(String limitId, String uri, String uriRegex, LinkedList<HttpMethod> methods) {
        final ConfiguredRatelimit configuredRateLimit = new ConfiguredRatelimit();

        configuredRateLimit.setId(limitId);
        configuredRateLimit.setUnit(TimeUnit.HOUR);
        configuredRateLimit.setUri(uri);
        configuredRateLimit.setUriRegex(uriRegex);
        configuredRateLimit.setValue(20);
        for (HttpMethod m : methods) {
            configuredRateLimit.getHttpMethods().add(m);
        }

        return configuredRateLimit;
    }
}
