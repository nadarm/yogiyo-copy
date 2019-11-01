package com.nadarm.yogiyo.ui.viewModel

import com.nadarm.yogiyo.data.repository.RestaurantRepository
import com.nadarm.yogiyo.ui.adapter.BaseListAdapter
import com.nadarm.yogiyo.ui.model.BaseItem
import com.nadarm.yogiyo.ui.model.Dish
import com.nadarm.yogiyo.ui.model.LabeledDishes
import com.nadarm.yogiyo.ui.model.Restaurant
import io.reactivex.Flowable
import io.reactivex.processors.BehaviorProcessor
import io.reactivex.processors.PublishProcessor
import io.reactivex.rxkotlin.addTo
import io.reactivex.rxkotlin.combineLatest
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface RestaurantDetailViewModel {

    interface Inputs : BaseListAdapter.Delegate {
        fun restaurantId(id: Long)
    }

    interface Outputs {
        fun restaurantInfo(): Flowable<Restaurant>
        fun numOfMenu(): Flowable<Int>
        fun dishItems(): Flowable<List<BaseItem>>
        fun showDishDetail(): Flowable<Dish>
    }


    class ViewModelImpl @Inject constructor(
        private val restaurantRepo: RestaurantRepository,
        private val stringMap: Map<String, String>
    ) : BaseViewModel(), Inputs, Outputs {

        private val restaurantId: PublishProcessor<Long> = PublishProcessor.create()
        private val dishItemClicked: PublishProcessor<Dish> = PublishProcessor.create()
        private val labelItemClicked: PublishProcessor<LabeledDishes> = PublishProcessor.create()

        private val restaurantInfo: BehaviorProcessor<Restaurant> = BehaviorProcessor.create()
        private val numOfMenu: BehaviorProcessor<Int> = BehaviorProcessor.create()
        private val showDishDetail: BehaviorProcessor<Dish> = BehaviorProcessor.create()
        private val dishItems: BehaviorProcessor<List<BaseItem>> = BehaviorProcessor.create()

        private val labelState: MutableMap<String, Boolean> = HashMap<String, Boolean>()
        private val labels: BehaviorProcessor<Map<String, Boolean>> = BehaviorProcessor.create()
        private val baseUrl = stringMap["baseUrl"]?: error("baseUrl error")
        private val token = stringMap["token"]?: error("token error")


        val inputs: Inputs = this
        val outputs: Outputs = this

        init {

            val detail = restaurantId
                .flatMapSingle { id ->
                    restaurantRepo.getRestaurantDetail(id, token)
                        .subscribeOn(Schedulers.io())
                }
                .subscribeOn(Schedulers.io())

            detail
                .map { it.restaurant }
                .subscribe(restaurantInfo)

            detail
                .map { it.numOfMenu }
                .subscribe(numOfMenu)

            val originalDishes = detail
                .map { it.labels }
                .doOnNext {
                    it.forEach { label ->
                        labelState[label.label] = false
                    }
                    labelState[it[0].label] = true
                    labels.onNext(labelState)
                }

            labels
                .combineLatest(originalDishes)
                .map {
                    val state = it.first
                    val list = it.second
                    val newList = MutableList<BaseItem>(0) { BaseItem.BlankItem }
                    list.forEach { item ->
                        newList.add(item)
                        if (state.getOrElse(item.label) { false }) {
                            item.dishes.forEach { dish ->
                                newList.add(dish)
                            }
                        }
                        newList.add(BaseItem.BlankItem)
                    }
                    return@map newList
                }
                .subscribe(dishItems)

            dishItemClicked
                .throttleFirst(500, TimeUnit.MILLISECONDS)
                .subscribe(showDishDetail)

            labelItemClicked
                .subscribeOn(Schedulers.computation())
                .subscribe {
                    labelState[it.label] = !(labelState.getOrElse(it.label) { false })
                    labels.onNext(labelState)
                }
                .addTo(compositeDisposable)

        }


        override fun restaurantInfo(): Flowable<Restaurant> = restaurantInfo
        override fun numOfMenu(): Flowable<Int> = numOfMenu
        override fun dishItems(): Flowable<List<BaseItem>> = dishItems
        override fun showDishDetail(): Flowable<Dish> = showDishDetail

        override fun itemClicked(item: BaseItem) {
            when (item) {
                is LabeledDishes -> labelItemClicked.onNext(item)
                is Dish -> dishItemClicked.onNext(item)
            }
        }

        override fun lastScrollPosition(position: Int) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun restaurantId(id: Long) {
            restaurantId.onNext(id)
        }
    }
}