package com.kshitijchauhan.haroldadmin.moviedb.ui.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.kshitijchauhan.haroldadmin.moviedb.repository.actors.ActorsRepository
import com.kshitijchauhan.haroldadmin.moviedb.repository.local.model.Actor
import com.kshitijchauhan.haroldadmin.moviedb.repository.local.model.Movie
import com.kshitijchauhan.haroldadmin.moviedb.repository.movie.MoviesRepository
import com.kshitijchauhan.haroldadmin.moviedb.utils.SingleLiveEvent
import com.kshitijchauhan.haroldadmin.moviedb.utils.extensions.disposeWith
import com.kshitijchauhan.haroldadmin.moviedb.utils.extensions.log
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.util.concurrent.TimeoutException

class MovieDetailsViewModel(
    private val isAuthenticated: Boolean,
    private val movieId: Int,
    private val moviesRepository: MoviesRepository,
    private val actorsRepository: ActorsRepository
) : ViewModel() {

    private val compositeDisposable = CompositeDisposable()
    private val _movie = MutableLiveData<Movie>()
    private val _cast = MutableLiveData<List<Actor>>()
    private val _message = SingleLiveEvent<String>()

    val movie: LiveData<Movie>
        get() = _movie

    val cast: LiveData<List<Actor>>
        get() = _cast

    val message: LiveData<String>
        get() = _message

    fun getMovieDetails(isAuthenticated: Boolean) {
        moviesRepository.getMovieDetails(movieId, isAuthenticated, compositeDisposable)
            .subscribeOn(Schedulers.io())
            .doOnNext { movie ->
                getActorsForMovie(movie.castIds, compositeDisposable)
            }
            .subscribe(
                // onNext
                { movie: Movie ->
                    _movie.postValue(movie)
                },
                // onError
                {
                    handleError(it)
                }
            )
            .disposeWith(compositeDisposable)
    }

    fun toggleMovieFavouriteStatus(accountId: Int) {
        if (isAuthenticated) {
            _movie.value?.isFavourited?.let {
                moviesRepository
                    .toggleMovieFavouriteStatus(!it, movieId, accountId)
                    .subscribe(
                        // OnNext
                        { log("Movie status updated successfully") },
                        // onError
                        { error -> handleError(error) }
                    )
                    .disposeWith(compositeDisposable)
            }
        } else throw IllegalStateException("Can't toggle favourite status if user is not logged in")
    }

    fun toggleMovieWatchlistStatus(accountId: Int) {
        if (isAuthenticated) {
            _movie.value?.isWatchlisted?.let {
                moviesRepository
                    .toggleMovieWatchlistStatus(!it, movieId, accountId)
                    .subscribe(
                        // OnNext
                        { log("Movie status updated successfully") },
                        // onError
                        { error -> handleError(error) }
                    )
                    .disposeWith(compositeDisposable)
            }
        } else throw IllegalStateException("Can't toggle watchlist status if user is not logged in")
    }

    private fun getActorsForMovie(actorIds: List<Int>, compositeDisposable: CompositeDisposable) {
        actorsRepository.getAllActors(actorIds, compositeDisposable)
            .subscribeOn(Schedulers.io())
            .subscribe(
                { actorsList ->
                    _cast.postValue(actorsList)
                },
                { error ->
                    handleError(error)
                }
            )
            .disposeWith(compositeDisposable)
    }

    override fun onCleared() {
        super.onCleared()
        compositeDisposable.dispose()
    }

    private fun handleError(error: Throwable) {
        log(error.localizedMessage)
        when (error) {
            is IOException -> _message.postValue("Please check your internet connection")
            is TimeoutException -> _message.postValue("Request timed out")
            else -> _message.postValue("An error occurred")
        }
    }
}
