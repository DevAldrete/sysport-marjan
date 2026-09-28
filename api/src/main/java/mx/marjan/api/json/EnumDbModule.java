package mx.marjan.api.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.micronaut.context.annotation.Bean;
import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Puts the domain enums on the wire as their database values ({@code in_transit},
 * {@code occasional}) instead of Java constant names, keeping core free of any
 * Jackson dependency: the mapping is reflected from {@code dbValue()} / {@code fromDb()}.
 */
@Bean
public class EnumDbModule extends SimpleModule {

    public EnumDbModule() {
        addSerializer(Enum.class, new EnumDbSerializer());
        setDeserializerModifier(new BeanDeserializerModifier() {
            @Override
            public JsonDeserializer<?> modifyEnumDeserializer(DeserializationConfig config,
                    JavaType valueType, BeanDescription beanDesc, JsonDeserializer<?> deserializer) {
                return new EnumDbDeserializer(valueType.getRawClass());
            }
        });
    }

    private static final class EnumDbSerializer extends StdSerializer<Enum> {

        EnumDbSerializer() {
            super(Enum.class);
        }

        @Override
        public void serialize(Enum value, JsonGenerator generator, SerializerProvider provider)
                throws IOException {
            generator.writeString(invokeString("dbValue", value));
        }
    }

    private static final class EnumDbDeserializer extends JsonDeserializer<Object> {

        private final Class<?> enumType;

        EnumDbDeserializer(Class<?> enumType) {
            this.enumType = enumType;
        }

        @Override
        public Object deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String value = parser.getValueAsString();
            try {
                Method fromDb = enumType.getMethod("fromDb", String.class);
                return fromDb.invoke(null, value);
            } catch (ReflectiveOperationException failure) {
                throw new IOException("No se puede leer " + enumType.getSimpleName() + " desde '" + value + "'",
                        failure);
            }
        }
    }

    private static String invokeString(String method, Enum value) {
        try {
            return (String) value.getClass().getMethod(method).invoke(value);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
