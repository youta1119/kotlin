interface A {
    fun foo()
    fun getA(): A
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

fun test_3(x: A?) {
    if (x?.getA() == null) return
    x.foo()
}

interface MyData{
    val s: String

    fun fs(): String
}

interface Q {
    val data: MyData?

    fun fdata(): MyData?
}

fun test_4(q: Q?) {
    if (q?.data?.s?.length != null) {
        q.data
        q.data.s
        q.data.s.length
    }
}

fun test_5(q: Q?) {
    q?.data?.s?.length ?: return
    q.data
    q.data.s
    q.data.s.length
}

fun test_6(q: Q?) {
    if (q?.fdata()?.fs()?.length != null) {
        q.fdata() // good
        q.fdata().fs() // bad
        q.fdata().fs().length // bad
    }
}