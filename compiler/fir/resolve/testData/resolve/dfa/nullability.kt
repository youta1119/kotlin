interface A {
    fun foo()
}

fun test_1(x: A?) {
    if (x != null) {
        x.foo()
    } else {
        x.foo()
    }
    x.foo()
}

fun test_2(x: A?) {
    if (x == null) {
        x.foo()
    } else {
        x.foo()
    }
    x.foo()
}