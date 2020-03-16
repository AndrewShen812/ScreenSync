package com.sy.screensync


import org.easydarwin.util.AbstractSubscriber
import org.reactivestreams.Publisher

import io.reactivex.Single
import io.reactivex.subjects.PublishSubject

/**
 *
 * @author ShenYong
 * @date 2020/3/16
 */
object RxHelper {
    internal var IGNORE_ERROR = false

    fun <T> single(t: Publisher<T>, defaultValueIfNotNull: T?): Single<T> {
        if (defaultValueIfNotNull != null) return Single.just(defaultValueIfNotNull)
        val sub: PublishSubject<T> = PublishSubject.create()
        t.subscribe(object : AbstractSubscriber<T>() {
            override fun onNext(t: T) {
                super.onNext(t)
                sub.onNext(t)
            }

            override fun onError(t: Throwable) {
                if (IGNORE_ERROR) {
                    super.onError(t)
                    sub.onComplete()
                } else {
                    sub.onError(t)
                }
            }

            override fun onComplete() {
                super.onComplete()
                sub.onComplete()
            }
        })
        return sub.firstOrError()
    }
}
