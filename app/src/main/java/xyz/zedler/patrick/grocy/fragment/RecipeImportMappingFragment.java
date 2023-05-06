/*
 * This file is part of Grocy Android.
 *
 * Grocy Android is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grocy Android is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grocy Android. If not, see http://www.gnu.org/licenses/.
 *
 * Copyright (c) 2020-2023 by Patrick Zedler and Dominic Zedler
 */

package xyz.zedler.patrick.grocy.fragment;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import xyz.zedler.patrick.grocy.Constants.FAB;
import xyz.zedler.patrick.grocy.R;
import xyz.zedler.patrick.grocy.activity.MainActivity;
import xyz.zedler.patrick.grocy.adapter.RecipeImportMappingAdapter;
import xyz.zedler.patrick.grocy.adapter.RecipeImportMappingAdapter.IngredientViewHolder;
import xyz.zedler.patrick.grocy.behavior.SystemBarBehavior;
import xyz.zedler.patrick.grocy.databinding.FragmentRecipeImportMappingBinding;
import xyz.zedler.patrick.grocy.helper.InfoFullscreenHelper;
import xyz.zedler.patrick.grocy.model.BottomSheetEvent;
import xyz.zedler.patrick.grocy.model.Event;
import xyz.zedler.patrick.grocy.model.QuantityUnit;
import xyz.zedler.patrick.grocy.model.RecipeParsed;
import xyz.zedler.patrick.grocy.model.RecipeParsed.Ingredient;
import xyz.zedler.patrick.grocy.model.RecipeParsed.IngredientPart;
import xyz.zedler.patrick.grocy.model.RecipeParsed.IngredientWord;
import xyz.zedler.patrick.grocy.model.SnackbarMessage;
import xyz.zedler.patrick.grocy.viewmodel.RecipeImportViewModel;
import xyz.zedler.patrick.grocy.viewmodel.RecipeImportViewModel.RecipeImportViewModelFactory;

public class RecipeImportMappingFragment extends BaseFragment implements IngredientViewHolder.OnWordClickListener {

  private final static String TAG = RecipeImportMappingFragment.class.getSimpleName();

  private MainActivity activity;
  private FragmentRecipeImportMappingBinding binding;
  private RecipeImportViewModel viewModel;
  private InfoFullscreenHelper infoFullscreenHelper;
  private RecipeImportMappingAdapter adapter;
  private boolean isFragmentInAssignmentState = false;

  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      ViewGroup container,
      Bundle savedInstanceState
  ) {
    binding = FragmentRecipeImportMappingBinding.inflate(inflater, container, false);
    return binding.getRoot();
  }

  @Override
  public void onDestroyView() {
    super.onDestroyView();
    if (infoFullscreenHelper != null) {
      infoFullscreenHelper.destroyInstance();
      infoFullscreenHelper = null;
    }
    binding = null;
  }

  @SuppressLint("NotifyDataSetChanged")
  @Override
  public void onViewCreated(@Nullable View view, @Nullable Bundle savedInstanceState) {
    activity = (MainActivity) requireActivity();
    RecipeImportGeneralFragmentArgs args = RecipeImportGeneralFragmentArgs
        .fromBundle(requireArguments());

    viewModel = new ViewModelProvider(this, new RecipeImportViewModelFactory(
        activity.getApplication(),
        null)
    ).get(RecipeImportViewModel.class);
    binding.setActivity(activity);
    binding.setViewModel(viewModel);
    binding.setFragment(this);
    binding.setLifecycleOwner(getViewLifecycleOwner());

    SystemBarBehavior systemBarBehavior = new SystemBarBehavior(activity);
    systemBarBehavior.setAppBar(binding.appBar);
    systemBarBehavior.setContainer(binding.swipe);
    systemBarBehavior.setRecycler(binding.recycler);
    systemBarBehavior.setUp();
    activity.setSystemBarBehavior(systemBarBehavior);

     RecipeParsed recipeParsed = RecipeImportGeneralFragmentArgs
         .fromBundle(requireArguments()).getRecipeParsed();
    if (viewModel.getRecipeParsed() == null) {
      viewModel.setRecipeParsed(recipeParsed);
    }

    binding.toolbar.setNavigationOnClickListener(v -> activity.navigateUp());

    infoFullscreenHelper = new InfoFullscreenHelper(binding.frame);

    viewModel.getInfoFullscreenLive().observe(
        getViewLifecycleOwner(),
        infoFullscreen -> infoFullscreenHelper.setInfo(infoFullscreen)
    );
    viewModel.getIsLoadingLive().observe(getViewLifecycleOwner(), isDownloading ->
        binding.swipe.setRefreshing(isDownloading)
    );
    viewModel.getEventHandler().observeEvent(getViewLifecycleOwner(), event -> {
      if (event.getType() == Event.SNACKBAR_MESSAGE) {
        activity.showSnackbar(
            ((SnackbarMessage) event).getSnackbar(activity.binding.coordinatorMain)
        );
      } else if (event.getType() == Event.BOTTOM_SHEET) {
        BottomSheetEvent bottomSheetEvent = (BottomSheetEvent) event;
        activity.showBottomSheet(bottomSheetEvent.getBottomSheet(), event.getBundle());
      }
    });

    binding.recycler.setLayoutManager(
        new LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)
    );
    adapter = new RecipeImportMappingAdapter(viewModel.getRecipeParsed(), this);
    binding.recycler.setAdapter(adapter);

    viewModel.getMappingEntityLive().observe(getViewLifecycleOwner(), entity -> {
      viewModel.updateAllWordsClickableState();
      adapter.notifyDataSetChanged();
    });

    if (savedInstanceState == null) viewModel.loadFromDatabase(true);

    activity.getScrollBehavior().setNestedOverScrollFixEnabled(true);
    activity.getScrollBehavior().setUpScroll(binding.appBar, false, binding.recycler);
    activity.getScrollBehavior().setBottomBarVisibility(true);
    activity.updateBottomAppBar(true, R.menu.menu_empty);
    activity.updateFab(
        R.drawable.ic_round_arrow_forward,
        R.string.action_import,
        FAB.TAG.IMPORT,
        savedInstanceState == null,
        () -> {
          isFragmentInAssignmentState = !isFragmentInAssignmentState;
          adapter.setFragmentInAssignmentState(isFragmentInAssignmentState);
          activity.showSnackbar("Assignment state changed", false);
          viewModel.getRecipeParsed().updateWordsState();
          adapter.notifyDataSetChanged();
        }
    );
  }

  @SuppressLint("NotifyDataSetChanged")
  @Override
  public void onWordClick(Ingredient ingredient, IngredientWord word, int position) {
    if (!isFragmentInAssignmentState) {
      ingredient.markWord(word, viewModel.getMappingEntityLive().getValue());
      adapter.notifyItemChanged(position);
    } else {
      if (word.isMarked() && word.getEntity().equals(IngredientPart.ENTITY_UNIT)) {
        IngredientPart part = ingredient.getPartFromWord(word);
        viewModel.openQuantityUnitsBottomSheet(part);
      }
    }
  }

  @Override
  public void selectQuantityUnit(QuantityUnit quantityUnit, Bundle argsBundle) {
    /*recipe.storeUnitAssignment(ingredient.getTextFromPart(part), 1);
    recipe.updateWordsState();*/
    adapter.notifyDataSetChanged();
  }

  @NonNull
  @Override
  public String toString() {
    return TAG;
  }
}
