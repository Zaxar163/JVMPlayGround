package ru.zaxar163.core;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.function.Supplier;

import ru.zaxar163.crasher.Crasher;
import ru.zaxar163.unsafe.fast.InvokerMethod;
import ru.zaxar163.unsafe.fast.ReflectionUtil;
import ru.zaxar163.unsafe.xlevel.ThreadList;

public final class JVMPlayGround {
	public static void main(final String... args) throws Throwable {
		ThreadList.getThreads().forEach((n, t) -> {
			System.out.println("Thread # " + n + " Data: " + t);
		});
		try {
			final Supplier<Object> classSameInstancer = ReflectionUtil
					.sameSizeObject(ClassLoader.getSystemClassLoader(), Class.class, Collections.emptyList());
			final InvokerMethod clI = ReflectionUtil
					.wrapConstructorNonInstance(Class.class.getDeclaredConstructors()[0]);
			final Class<?> a = (Class<?>) ReflectionUtil.changeObjFullUnsafe(Class.class, classSameInstancer.get());
			clI.invoke(a, ClassLoader.getSystemClassLoader());
			System.out.println(a.getClassLoader());
			clI.invoke(a, new URLClassLoader(new URL[0]));
			System.out.println(a.getClassLoader());
		} catch (final Throwable e) {
			e.printStackTrace();
		}
		Crasher.crashZip();
	}

	private JVMPlayGround() {
	}
}