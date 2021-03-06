// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.android.xml;

import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.build.android.AndroidDataWritingVisitor;
import com.google.devtools.build.android.AndroidDataWritingVisitor.ValuesResourceDefinition;
import com.google.devtools.build.android.AndroidResourceClassWriter;
import com.google.devtools.build.android.DataSource;
import com.google.devtools.build.android.FullyQualifiedName;
import com.google.devtools.build.android.XmlResourceValue;
import com.google.devtools.build.android.XmlResourceValues;
import com.google.devtools.build.android.proto.SerializeFormat;
import com.google.devtools.build.android.proto.SerializeFormat.DataValueXml.XmlType;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Represents an Android Style Resource.
 *
 * <p>
 * Styles (http://developer.android.com/guide/topics/resources/style-resource.html) define a look
 * and feel for a layout or other ui construct. They are effectively a s set of values that
 * correspond to &lt;attr&gt; resources defined either in the base android framework or in other
 * resources. They also allow inheritance on other styles. For a style to valid in a given resource
 * pass, they must only contain definer attributes with acceptable values. <code>
 *   &lt;resources&gt;
 *     &lt;style name="CustomText" parent="@style/Text"&gt;
 *       &lt;item name="android:textSize"&gt;20sp&lt;/item&gt;
 *       &lt;item name="android:textColor"&gt;#008&lt;/item&gt;
 *     &lt;/style&gt;
 *  &lt;/resources&gt;
 * </code>
 */
@Immutable
public class StyleXmlResourceValue implements XmlResourceValue {
  public static final Function<Entry<String, String>, String> ENTRY_TO_ITEM =
      new Function<Entry<String, String>, String>() {
        @Nullable
        @Override
        public String apply(Entry<String, String> input) {
          return String.format("<item name='%s'>%s</item>", input.getKey(), input.getValue());
        }
      };
  private final String parent;
  private final ImmutableMap<String, String> values;

  public static StyleXmlResourceValue of(String parent, Map<String, String> values) {
    return new StyleXmlResourceValue(parent, ImmutableMap.copyOf(values));
  }

  @SuppressWarnings("deprecation")
  public static XmlResourceValue from(SerializeFormat.DataValueXml proto) {
    return of(proto.hasValue() ? proto.getValue() : null, proto.getMappedStringValue());
  }

  private StyleXmlResourceValue(@Nullable String parent, ImmutableMap<String, String> values) {
    this.parent = parent;
    this.values = values;
  }

  @Override
  public void write(
      FullyQualifiedName key, DataSource source, AndroidDataWritingVisitor mergedDataWriter) {

    ValuesResourceDefinition definition =
        mergedDataWriter
            .define(key)
            .derivedFrom(source)
            .startTag("style")
            .named(key)
            .optional()
            .attribute("parent")
            .setTo(parent)
            .closeTag()
            .addCharactersOf("\n");
    for (Entry<String, String> entry : values.entrySet()) {
      definition =
          definition
              .startItemTag()
              .named(entry.getKey())
              .closeTag()
              .addCharactersOf(entry.getValue())
              .endTag()
              .addCharactersOf("\n");
    }
    definition.endTag().save();
  }

  @Override
  public void writeResourceToClass(FullyQualifiedName key,
      AndroidResourceClassWriter resourceClassWriter) {
    resourceClassWriter.writeSimpleResource(key.type(), key.name());
  }

  @Override
  public int serializeTo(int sourceId, Namespaces namespaces, OutputStream output)
      throws IOException {
    SerializeFormat.DataValueXml.Builder xmlValueBuilder =
        SerializeFormat.DataValueXml.newBuilder()
            .setType(XmlType.STYLE)
            .putAllNamespace(namespaces.asMap())
            .putAllMappedStringValue(values);
    if (parent != null) {
      xmlValueBuilder.setValue(parent);
    }
    return XmlResourceValues.serializeProtoDataValue(
        output,
        XmlResourceValues.newSerializableDataValueBuilder(sourceId).setXmlValue(xmlValueBuilder));
  }

  @Override
  public int hashCode() {
    return Objects.hash(parent, values);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof StyleXmlResourceValue)) {
      return false;
    }
    StyleXmlResourceValue other = (StyleXmlResourceValue) obj;
    return Objects.equals(parent, other.parent) && Objects.equals(values, other.values);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(getClass())
        .add("parent", parent)
        .add("values", values)
        .toString();
  }

  @Override
  public XmlResourceValue combineWith(XmlResourceValue value) {
    throw new IllegalArgumentException(this + " is not a combinable resource.");
  }
  
  @Override
  public String asConflictStringWith(DataSource source) {
    return source.asConflictString();
  }
}
