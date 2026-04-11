package com.fittribe.api.config;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

import java.io.IOException;
import java.util.Properties;

/**
 * Loads a YAML file as a {@link PropertySource} so that {@link org.springframework.context.annotation.PropertySource}
 * can be used for non-{@code application.yml} config files.
 *
 * Spring Boot's default {@code DefaultPropertySourceFactory} only understands
 * {@code .properties}; YAML files have to be adapted via
 * {@link YamlPropertiesFactoryBean}. This class is the minimal adapter that lets
 * {@code @PropertySource(value = "classpath:findings-templates.yml", factory = YamlPropertySourceFactory.class)}
 * work alongside {@link org.springframework.boot.context.properties.ConfigurationProperties}.
 *
 * Only used by {@link FindingsConfig} — don't add instance state here.
 */
public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(resource.getResource());

        Properties properties = factory.getObject();
        if (properties == null) {
            throw new IOException("Failed to load YAML properties from " + resource.getResource());
        }

        String sourceName = (name != null) ? name : resource.getResource().getFilename();
        if (sourceName == null) {
            sourceName = "yaml-source";
        }
        return new PropertiesPropertySource(sourceName, properties);
    }
}
