package com.nadarm.yogiyo.ui.viewModel

import com.nadarm.yogiyo.data.repository.AdRepository
import com.nadarm.yogiyo.ui.adapter.AutoScrollCircularListAdapter
import com.nadarm.yogiyo.ui.model.Ad
import com.nadarm.yogiyo.ui.model.BaseItem
import io.reactivex.Flowable
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.PublishProcessor
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject


interface AutoScrollAdViewModel {

    interface Inputs : AutoScrollCircularListAdapter.Delegate {
        fun setAdType(type: Ad.Type)
    }

    interface Outputs {
        fun adItemList(): Flowable<List<BaseItem>>
    }

    class ViewModelImpl @Inject constructor(
        private val adRepository: AdRepository
    ) : BaseViewModel(), Inputs, Outputs {

        private val itemClicked: PublishProcessor<BaseItem> = PublishProcessor.create()
        private val adType: PublishProcessor<Ad.Type> = PublishProcessor.create()

        private val adItemList: BehaviorProcessor<List<BaseItem>> = BehaviorProcessor.create()

        val inputs: Inputs = this
        val outputs: Outputs = this

        init {

            adType.flatMapSingle { type ->
                adRepository.getAds(type)
                    .subscribeOn(Schedulers.io())
            }
                .subscribe { adItemList.onNext(it) }
                .addTo(compositeDisposable)


        }

        override fun adItemList(): Flowable<List<BaseItem>> = adItemList

        override fun itemClicked(item: BaseItem) {
            itemClicked.onNext(item)
        }

        override fun setAdType(type: Ad.Type) {
            adType.onNext(type)
        }
    }

}
