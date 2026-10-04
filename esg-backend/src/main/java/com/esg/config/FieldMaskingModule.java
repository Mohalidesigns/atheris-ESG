package com.esg.config;

import com.esg.security.CurrentUser;
import com.esg.security.FieldPermission;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.SerializationConfig;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Masks {@link FieldPermission}-annotated properties to null for callers without the permission. */
@Component
public class FieldMaskingModule extends SimpleModule {

    public FieldMaskingModule() {
        super("field-masking");
        setSerializerModifier(new BeanSerializerModifier() {
            @Override
            public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription description,
                                                             List<BeanPropertyWriter> properties) {
                List<BeanPropertyWriter> result = new ArrayList<>(properties.size());
                for (BeanPropertyWriter writer : properties) {
                    FieldPermission permission = writer.getAnnotation(FieldPermission.class);
                    result.add(permission == null ? writer : new MaskingWriter(writer, permission.value()));
                }
                return result;
            }
        });
    }

    private static final class MaskingWriter extends BeanPropertyWriter {
        private final String permission;

        MaskingWriter(BeanPropertyWriter base, String permission) {
            super(base);
            this.permission = permission;
        }

        @Override
        public void serializeAsField(Object bean, JsonGenerator gen, SerializerProvider prov) throws Exception {
            if (CurrentUser.hasPermission(permission)) {
                super.serializeAsField(bean, gen, prov);
            } else {
                gen.writeNullField(getName());
            }
        }
    }
}
