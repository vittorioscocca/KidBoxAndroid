package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TravelDestinationSearchViewModel @Inject constructor(
    val searchService: TravelPlaceSearchService,
) : ViewModel()
