package ru.zaxar163.util.dynamicgen;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import ru.zaxar163.util.ClassUtil;
import ru.zaxar163.util.LookupUtil;

/**
 * For lots of instances.
 */
public final class FastDynamicProxy<T> {
	private static final Type EXCEPTION_NOT_IMPL = Type.getType(IllegalStateException.class);

	private static Type[] typify(final Class<?>[] clazzs) {
		final Type[] types = new Type[clazzs.length];
		for (int i = 0; i < types.length; i++)
			types[i] = Type.getType(clazzs[i]);
		return types;
	}

	private final Class<?> clazz;

	private final ClassLoader loader;
	private final Class<T> proxy;

	private final MethodHandle proxyC;

	public FastDynamicProxy(final ClassLoader loader, final Class<?> clazz, final Class<T> proxy) {
		this.loader = ProxyData.forProxy(loader).add(clazz).add(proxy);
		this.clazz = clazz;
		this.proxy = proxy;
		this.proxyC = emitProxy();
	}

	private java.lang.reflect.Method asGet(final String name, final int args, final boolean isOptional) {
		return Arrays.stream(LookupUtil.getDeclaredMethods(clazz)).filter(e -> name.equals(e.getName()))
				.filter(e -> e.getParameterCount() == args).findFirst().orElse(ProxyData.unsupported);
	}

	private void emit(final Map<java.lang.reflect.Method, java.lang.reflect.Method> methods, final ClassVisitor cw,
			final Type vt, final Type sn) {
		final Type handleT = Type.getType(clazz);
		final MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
		init.visitCode();
		init.visitVarInsn(Opcodes.ALOAD, 0);
		init.visitMethodInsn(Opcodes.INVOKESPECIAL, sn.getInternalName(), "<init>", "()V", false);
		init.visitInsn(Opcodes.RETURN);
		init.visitMaxs(-1, -1);
		init.visitEnd();
		for (final Map.Entry<java.lang.reflect.Method, java.lang.reflect.Method> method : methods.entrySet()) {
			final GeneratorAdapter m = new GeneratorAdapter(Opcodes.ACC_PUBLIC, Method.getMethod(method.getKey()), null,
					typify(method.getKey().getExceptionTypes()), cw);
			m.visitAnnotation(ProxyData.FORCE_INLINE, true).visitEnd();
			m.visitCode();
			if (method.getValue() == ProxyData.unsupported) // only in 1 case
				m.throwException(EXCEPTION_NOT_IMPL, "Method \"" + method.getKey().getName()
						+ "\" not found in proxified class \"" + clazz.getName() + '\"');
			else {
				m.loadArgs();
				if (Modifier.isStatic(method.getValue().getModifiers()))
					m.invokeStatic(handleT, Method.getMethod(method.getValue()));
				else
					m.invokeVirtual(handleT, Method.getMethod(method.getValue()));
				m.returnValue();
			}
			m.visitEnd();
		}
	}

	private MethodHandle emitProxy() {
		final Map<java.lang.reflect.Method, java.lang.reflect.Method> methods = Arrays
				.stream(proxy.getDeclaredMethods())
				.collect(Collectors.toMap(m -> m,
						m -> m.isAnnotationPresent(RealName.class)
								? asGet(m.getAnnotation(RealName.class).value(), params(m),
										m.isAnnotationPresent(OptionalMethod.class))
								: asGet(m.getName(), params(m), m.isAnnotationPresent(OptionalMethod.class))));
		final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		final String name = ProxyData.nextName();
		cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, ProxyData.MAGIC_SUPER,
				new String[] { Type.getInternalName(proxy) });
		emit(methods, cw, Type.getObjectType(name), Type.getObjectType(ProxyData.MAGIC_SUPER));
		cw.visitEnd();
		final byte[] code = cw.toByteArray();
		try {
			return LookupUtil.ALL_LOOKUP.unreflectConstructor(
					ClassUtil.defineClass(loader, name, code, 0, code.length, null).getDeclaredConstructors()[0]);
		} catch (final Throwable e) {
			throw new RuntimeException(e);
		}
	}

	public T instance() {
		try {
			return (T) proxyC.invoke();
		} catch (final Throwable e) {
			throw new RuntimeException(e);
		}
	}

	private int params(final java.lang.reflect.Method m) {
		return m.isAnnotationPresent(Static.class) ? m.getParameterCount() : m.getParameterCount() - 1;
	}
}
