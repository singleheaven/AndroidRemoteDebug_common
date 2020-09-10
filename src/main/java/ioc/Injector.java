package ioc;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Qualifier;
import javax.inject.Singleton;

public class Injector {
    private static class InjectException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public InjectException() {
            super();
        }

        public InjectException(String message, Throwable cause) {
            super(message, cause);
        }

        public InjectException(String message) {
            super(message);
        }

        public InjectException(Throwable cause) {
            super(cause);
        }

    }

    private Injector() {

    }

    private static Injector INJECTOR;

    static {
        INJECTOR = new Injector();
    }

    // 已经生成的单例实例放在这里，后续注入处可以直接拿
    private final Map<Class<?>, Object> singletons = Collections.synchronizedMap(new HashMap<>());

    {
        singletons.put(Injector.class, this);
    }

    // 已经生成的限定器实例放在这里，可续注入处可以直接拿
    // 限定器就是在单例基础上增加一个类别，相当于多种单例，用Annotation来限定具体哪个单例
    private final Map<Class<?>, Map<Annotation, Object>> qualifieds = Collections.synchronizedMap(new HashMap<>());

    // 尚未初始化的单例类放在这里
    private final Map<Class<?>, Class<?>> singletonClasses = Collections.synchronizedMap(new HashMap<>());

    // 尚未初始化的限定类别单例类放在这里
    private final Map<Class<?>, Map<Annotation, Class<?>>> qualifiedClasses = Collections.synchronizedMap(new HashMap<>());

    // 准备进行构造的类
    private final Set<Class<?>> readyClasses = Collections.synchronizedSet(new HashSet<>());

    private <T> Injector registerSingleton(Class<T> clazz, T o) {
        if (singletons.put(clazz, o) != null) {
            throw new InjectException("duplicated singleton object for the same class " + clazz.getCanonicalName());
        }
        return this;
    }

    private <T> Injector registerQualified(Class<T> clazz, Annotation anno, T o) {
        if (!anno.annotationType().isAnnotationPresent(Qualifier.class)) {
            throw new InjectException(
                    "annotation must be decorated with Qualifier " + anno.annotationType().getCanonicalName());
        }
        Map<Annotation, Object> os = qualifieds.get(clazz);
        if (os == null) {
            os = Collections.synchronizedMap(new HashMap<>());
            qualifieds.put(clazz, os);
        }
        if (os.put(anno, o) != null) {
            throw new InjectException(
                    String.format("duplicated qualified object with the same qualifier %s with the class %s",
                            anno.annotationType().getCanonicalName(), clazz.getCanonicalName()));
        }
        return this;
    }

    private <T> Injector registerSingletonClass(Class<T> clazz) {
        return this.registerSingletonClass(clazz, clazz);
    }

    private <T> Injector registerSingletonClass(Class<?> parentType, Class<T> clazz) {
        if (singletonClasses.put(parentType, clazz) != null) {
            throw new InjectException("duplicated singleton class " + parentType.getCanonicalName());
        }
        return this;
    }

    public static <T> Injector registerQualifiedClass(Class<?> parentType, Class<T> clazz) {
        for (Annotation anno : clazz.getAnnotations()) {
            if (anno.annotationType().isAnnotationPresent(Qualifier.class)) {
                return INJECTOR.registerQualifiedClass(parentType, anno, clazz);
            }
        }
        throw new InjectException("class should decorated with annotation tagged by Qualifier");
    }

    private <T> Injector registerQualifiedClass(Class<?> parentType, Annotation anno, Class<T> clazz) {
        if (!anno.annotationType().isAnnotationPresent(Qualifier.class)) {
            throw new InjectException(
                    "annotation must be decorated with Qualifier " + anno.annotationType().getCanonicalName());
        }
        Map<Annotation, Class<?>> annos = qualifiedClasses.get(parentType);
        if (annos == null) {
            annos = Collections.synchronizedMap(new HashMap<>());
            qualifiedClasses.put(parentType, annos);
        }
        if (annos.put(anno, clazz) != null) {
            throw new InjectException(String.format("duplicated qualifier %s with the same class %s",
                    anno.annotationType().getCanonicalName(), parentType.getCanonicalName()));
        }
        return this;
    }

    private <T> T createNew(Class<T> clazz) {
        return this.createNew(clazz, null);
    }

    @SuppressWarnings("unchecked")
    private <T> T createNew(Class<T> clazz, Consumer<T> consumer) {
        Object o = singletons.get(clazz);
        if (o != null) {
            return (T) o;
        }

        List<Constructor<T>> cons = new ArrayList<>();
        T target = null;
        for (Constructor<?> con : clazz.getDeclaredConstructors()) {
            // 默认构造期不需要Inject注解createFromQualified
            if (!con.isAnnotationPresent(Inject.class) && con.getParameterTypes().length > 0) {
                continue;
            }
            try {
                con.setAccessible(true);
            } catch (SecurityException e) {
                continue;
            }
            cons.add((Constructor<T>) con);
        }
        if (cons.size() > 1) {
            throw new InjectException("dupcated constructor for injection class " + clazz.getCanonicalName());
        }
        if (cons.size() == 0) {
            throw new InjectException("no accessible constructor for injection class " + clazz.getCanonicalName());
        }
        readyClasses.add(clazz); // 放入表示未完成的容器

        target = createFromConstructor(cons.get(0)); // 构造器注入

        readyClasses.remove(clazz); // 从未完成的容器取出

        boolean isSingleton = clazz.isAnnotationPresent(Singleton.class);
        if (!isSingleton) {
            isSingleton = this.singletonClasses.containsKey(clazz);
        }
        if (isSingleton) {
            singletons.put(clazz, target);
        }
        if (consumer != null) {
            consumer.accept(target);
        }

        injectMembers(target);

        return target;
    }

    private <T> T createFromConstructor(Constructor<T> con) {
        Class<?>[] parameterTypes = con.getParameterTypes();
        Annotation[][] annotations = con.getParameterAnnotations();
        Object[] params = new Object[parameterTypes.length];
        for (int i = 0; i < params.length; i++) {
            if (readyClasses.contains(parameterTypes[i])) {
                throw new InjectException(String.format("circular dependency on constructor , the root class is %s", con.getDeclaringClass().getCanonicalName()));
            }
            T param = createFromParameter(parameterTypes[i], annotations[i]);
            if (param == null) {
                throw new InjectException(String.format("parameter should not be empty with index %d of class %s",
                        i, con.getDeclaringClass().getCanonicalName()));
            }
            params[i] = param;
        }
        try {
            return con.newInstance(params);
        } catch (Exception e) {
            throw new InjectException("create instance from constructor error", e);
        }
    }

    private <T> T createFromParameter(Class<?> clazz, Annotation[] annotations) {
        T t = createFromQualified(clazz, annotations);
        if (t != null) {
            return t;
        }
        return (T) createNew(clazz);
    }

    @SuppressWarnings("unchecked")
    private <T> T createFromField(Field field) {
        Class<?> clazz = field.getType();
        T t = createFromQualified(field.getType(), field.getAnnotations());
        if (t != null) {
            return t;
        }
        return (T) createNew(clazz);
    }

    @SuppressWarnings("unchecked")
    private <T> T createFromQualified(Class<?> clazz, Annotation[] annos) {
        Map<Annotation, Object> qs = qualifieds.get(clazz);
        if (qs != null) {
            Set<Object> os = new HashSet<>();
            for (Annotation anno : annos) {
                Object o = qs.get(anno);
                if (o != null) {
                    os.add(o);
                }
            }
            if (os.size() > 1) {
                throw new InjectException(String.format("duplicated qualified object for field %s",
                        clazz.getCanonicalName()));
            }
            if (!os.isEmpty()) {
                return (T) (os.iterator().next());
            }
        }
        Map<Annotation, Class<?>> qz = qualifiedClasses.get(clazz);
        if (qz != null) {
            Set<Class<?>> oz = new HashSet<>();
            Annotation annoz = null;
            for (Annotation anno : annos) {
                Class<?> z = qz.get(anno);
                if (z != null) {
                    oz.add(z);
                    annoz = anno;
                }
            }
            if (oz.size() > 1) {
                throw new InjectException(String.format("duplicated qualified classes for field %s",
                        clazz.getCanonicalName()));
            }
            if (!oz.isEmpty()) {
                final Annotation annozRead = annoz;
                T t = (T) createNew(oz.iterator().next(), (o) -> {
                    this.registerQualified((Class<T>) clazz, annozRead, (T) o);
                });
                return t;
            }
        }
        return null;
    }

    /**
     * 注入成员
     *
     * @param t
     */
    private <T> void injectMembers(T t) {
        List<Field> fields = new ArrayList<>();
        for (Field field : t.getClass().getDeclaredFields()) {
            if (field.isAnnotationPresent(Inject.class)) {
                try {
                    field.setAccessible(true);
                    fields.add(field);
                } catch (SecurityException e) {
                    e.printStackTrace();
                }
            }
        }
        for (Field field : fields) {
            Object f = createFromField(field);
            try {
                field.set(t, f);
            } catch (Exception e) {
                throw new InjectException(
                        String.format("set field for %s@%s error", t.getClass().getCanonicalName(), field.getName()),
                        e);
            }
        }
    }

    /**
     * 获取对象
     *
     * @param clazz
     * @return
     */
    public static <T> T getInstance(Class<T> clazz) {
        return INJECTOR.createNew(clazz);
    }
}
