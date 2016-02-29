/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2014, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.process.spatialstatistics.transformation;

import java.util.NoSuchElementException;
import java.util.logging.Logger;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.AttributeTypeBuilder;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.util.logging.Logging;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.AttributeDescriptor;
import org.opengis.feature.type.PropertyDescriptor;

/**
 * Merge SimpleFeatureCollection Implementation
 * 
 * @author Minpa Lee, MangoSystem
 * 
 * @source $URL$
 */
public class MergeFeatureCollection extends GXTSimpleFeatureCollection {
    protected static final Logger LOGGER = Logging.getLogger(MergeFeatureCollection.class);

    private SimpleFeatureCollection features;

    private SimpleFeatureType schema;

    public MergeFeatureCollection(SimpleFeatureCollection delegate, SimpleFeatureCollection features)
            throws ClassNotFoundException {
        super(delegate);

        this.features = features;
        this.schema = buildTargetSchema();
    }

    private SimpleFeatureType buildTargetSchema() {
        // Create schema containing the attributes from both the feature collections
        SimpleFeatureTypeBuilder tb = new SimpleFeatureTypeBuilder();
        for (AttributeDescriptor descriptor : delegate.getSchema().getAttributeDescriptors()) {
            if (sameNames(features.getSchema(), descriptor)
                    && !sameTypes(features.getSchema(), descriptor)) {
                AttributeTypeBuilder builder = new AttributeTypeBuilder();
                builder.setName(descriptor.getLocalName());
                builder.setNillable(descriptor.isNillable());
                builder.setBinding(String.class); // to string
                builder.setMinOccurs(descriptor.getMinOccurs());
                builder.setMaxOccurs(descriptor.getMaxOccurs());
                builder.setDefaultValue(descriptor.getDefaultValue());
                builder.setCRS(delegate.getSchema().getCoordinateReferenceSystem());
                AttributeDescriptor attributeDescriptor = builder.buildDescriptor(
                        descriptor.getName(), builder.buildType());
                tb.add(attributeDescriptor);
            } else {
                tb.add(descriptor);
            }
        }

        for (AttributeDescriptor descriptor : features.getSchema().getAttributeDescriptors()) {
            if (!sameNames(delegate.getSchema(), descriptor)
                    && !sameTypes(delegate.getSchema(), descriptor)) {
                tb.add(descriptor);
            }
        }

        tb.setCRS(delegate.getSchema().getCoordinateReferenceSystem());
        tb.setNamespaceURI(delegate.getSchema().getName().getNamespaceURI());
        tb.setName(delegate.getSchema().getName());
        return tb.buildFeatureType();
    }

    @Override
    public SimpleFeatureIterator features() {
        return new MergeFeatureIterator(delegate, features, getSchema());
    }

    @Override
    public SimpleFeatureType getSchema() {
        return this.schema;
    }

    @Override
    public int size() {
        return delegate.size() + features.size();
    }

    @Override
    public ReferencedEnvelope getBounds() {
        ReferencedEnvelope bounds = delegate.getBounds();
        bounds.include(features.getBounds());
        return bounds;
    }

    private boolean sameNames(SimpleFeatureType schema, AttributeDescriptor f) {
        for (AttributeDescriptor descriptor : schema.getAttributeDescriptors()) {
            if (descriptor.getName().equals(f.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean sameTypes(SimpleFeatureType schema, AttributeDescriptor f) {
        for (AttributeDescriptor descriptor : schema.getAttributeDescriptors()) {
            if (descriptor.getType().equals(f.getType())) {
                return true;
            }
        }
        return false;
    }

    static class MergeFeatureIterator implements SimpleFeatureIterator {

        private SimpleFeatureIterator firstDelegate;

        private SimpleFeatureIterator secondDelegate;

        private SimpleFeatureBuilder builder;

        private SimpleFeature next;

        private int featureID = 0;

        public MergeFeatureIterator(SimpleFeatureCollection firstCollection,
                SimpleFeatureCollection secondCollection, SimpleFeatureType schema) {
            this.firstDelegate = firstCollection.features();
            this.secondDelegate = secondCollection.features();
            this.builder = new SimpleFeatureBuilder(schema);
        }

        public void close() {
            firstDelegate.close();
            secondDelegate.close();
        }

        public boolean hasNext() {
            while (next == null && firstDelegate.hasNext()) {
                SimpleFeature f = firstDelegate.next();
                for (PropertyDescriptor property : builder.getFeatureType().getDescriptors()) {
                    builder.set(property.getName(), f.getAttribute(property.getName()));
                }
                next = builder.buildFeature(Integer.toString(featureID));
                builder.reset();
                featureID++;
            }

            while (next == null && secondDelegate.hasNext() && !firstDelegate.hasNext()) {
                SimpleFeature f = secondDelegate.next();
                for (PropertyDescriptor property : builder.getFeatureType().getDescriptors()) {
                    builder.set(property.getName(), f.getAttribute(property.getName()));
                }
                next = builder.buildFeature(Integer.toString(featureID));
                builder.reset();
                featureID++;
            }

            return next != null;
        }

        public SimpleFeature next() throws NoSuchElementException {
            if (!hasNext()) {
                throw new NoSuchElementException("hasNext() returned false!");
            }

            SimpleFeature result = next;
            next = null;
            return result;
        }

    }
}