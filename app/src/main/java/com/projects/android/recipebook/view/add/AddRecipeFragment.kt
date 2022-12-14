package com.projects.android.recipebook.view.add

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.text.*
import android.text.method.LinkMovementMethod
import android.view.*
import android.view.View.*
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.projects.android.recipebook.R
import com.projects.android.recipebook.databinding.FragmentAddRecipeBinding
import com.projects.android.recipebook.databinding.ItemAddIngredientBinding
import com.projects.android.recipebook.model.Ingredient
import com.projects.android.recipebook.model.Recipe
import com.projects.android.recipebook.model.enums.Course
import com.projects.android.recipebook.model.enums.PreparationTime
import com.projects.android.recipebook.model.enums.UnitOfMeasure
import com.projects.android.recipebook.view.add.tag.AddSelectRecipeTagFragment
import com.projects.android.recipebook.view.add.tag.AddSelectRecipeTagFragment.DialogListener
import com.projects.android.recipebook.view.add.tag.TagSpan
import com.projects.android.recipebook.view.add.utils.AddRecipeCheckErrors
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AddRecipeFragment : Fragment() {

	private var container: ViewGroup? = null
	private var isDeleting = false
	private var textWatcherPreparationAdd: TextWatcher? = null

	// VIEW MODEL
	private val addRecipeViewModel: AddRecipeViewModel by viewModels() {
		AddRecipeViewModelFactory(args.recipeID)
	}

	// SAFE ARGS
	private val args: AddRecipeFragmentArgs by navArgs()

	// VIEW BINDING
	private var _binding: FragmentAddRecipeBinding? = null
	private val binding
		get() = checkNotNull(_binding) {
			"Cannot access binding because it is null. Is the view visible?"
		}

	private var _bindingIngredientsList = mutableListOf<ItemAddIngredientBinding?>()

	// Variables for taking photos
	private var photoName: String? = null
	private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { didTakePhoto: Boolean ->
		if (didTakePhoto && photoName != null) {
			addRecipeViewModel.updateState { it.photoFileName = photoName }
		}
	}

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		this.container = container
		_binding = FragmentAddRecipeBinding.inflate(layoutInflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)

		// APPBAR: MENU
		setupMenu()

		binding.apply {

			// Conditions for navigateUp: if there are no errors, navigateUp
			activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, true) {
				if (addRecipeViewModel.checkRecipe(binding, _bindingIngredientsList)) findNavController().navigateUp()
			}

			// ERRORS HANDLERS
			nameAdd.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
				if (!hasFocus) AddRecipeCheckErrors.checkName(nameLayoutAdd, addRecipeViewModel.state.value?.name)
			}
			portionsAdd.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
				if (!hasFocus) AddRecipeCheckErrors.checkPortions(
					portionsLayoutAdd, addRecipeViewModel.state.value?.portions
				)
			}
			preparationAdd.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
				if (!hasFocus) AddRecipeCheckErrors.checkPreparation(preparationLayoutAdd, addRecipeViewModel.state.value?.preparationEditable)
			}

			// Initializations
			requireContext().let {
				// Initialization spinner Course
				ArrayAdapter(
					it, android.R.layout.simple_spinner_dropdown_item, Course.values()
				).also { adapter ->
					(courseAdd.editText as AutoCompleteTextView).setAdapter(adapter)
				}
				// Initialization spinner PreparationTime
				ArrayAdapter(
					it, android.R.layout.simple_spinner_dropdown_item, PreparationTime.values()
				).also { adapter ->
					(preparationTimeAdd.editText as AutoCompleteTextView).setAdapter(adapter)
				}
				// Initialization spinner UnitOfMeasure
				ArrayAdapter(
					it, android.R.layout.simple_spinner_dropdown_item, UnitOfMeasure.values()
				).also { adapter ->
					(unitIngredientAdd.editText as AutoCompleteTextView).setAdapter(adapter)
				}
			}

			// Listeners UI->ViewModel (apply the changes to ViewModel when change the data on UI)
			nameAdd.doOnTextChanged { text, _, _, _ ->
				addRecipeViewModel.updateState { it.name = text.toString() }
			}

			takePhotoAdd.isEnabled = canResolveIntent(takePhoto.contract.createIntent(requireContext(), Uri.EMPTY))
			takePhotoAdd.setOnClickListener {
				photoName = "IMG_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ITALY).format(Date())}.JPG"
				val photoFile = File(requireContext().applicationContext.filesDir, photoName!!)
				val photoUri = FileProvider.getUriForFile(requireContext(), "com.projects.android.recipebook.fileprovider", photoFile)
				takePhoto.launch(photoUri)
			}

			isVegAdd.setOnCheckedChangeListener { _, b ->
				addRecipeViewModel.updateState { it.isVeg = b }
			}
			isCookedAdd.setOnCheckedChangeListener { _, b ->
				addRecipeViewModel.updateState { it.isCooked = b }
			}

			(courseAdd.editText as AutoCompleteTextView).onItemClickListener = OnItemClickListener { _, _, position, _ ->
				addRecipeViewModel.updateState {
					it.course = Course.values()[position]
				}
			}

			(preparationTimeAdd.editText as AutoCompleteTextView).onItemClickListener = OnItemClickListener { _, _, position, _ ->
				addRecipeViewModel.updateState {
					it.preparationTime = PreparationTime.values()[position]
				}
			}

			portionsAdd.doOnTextChanged { text, _, _, _ ->
				addRecipeViewModel.updateState {
					it.portions = text.toString()
				}
			}

			(unitIngredientAdd.editText as AutoCompleteTextView).onItemClickListener = OnItemClickListener { _, _, position, _ ->
				// Manage UnitOfMeasure.TO_TASTE
				if (position == UnitOfMeasure.TO_TASTE.ordinal) {
					quantityIngredientAdd.setText("")
					quantityIngredientAdd.visibility = GONE
				} else {
					quantityIngredientAdd.visibility = VISIBLE
				}
				addRecipeViewModel.updateState {
					it.unitIngredient = UnitOfMeasure.values()[position]
				}
				nameIngredientAdd.requestFocus()
			}

			nameIngredientAdd.setOnEditorActionListener { _, actionId, _ ->
				return@setOnEditorActionListener when (actionId) {
					EditorInfo.IME_ACTION_DONE -> {
						// If nameIngredientAdd is present and (quantityIngredientAdd is present or unitIngredientAdd==TO_TASTE)
						if ((!quantityIngredientAdd.text.isNullOrBlank() || addRecipeViewModel.state.value?.unitIngredient == UnitOfMeasure.TO_TASTE) && !nameIngredientAdd.text.isNullOrBlank()) {
							addIngredient(true)
						}
						true
					}

					else -> false
				}
			}

			textWatcherPreparationAdd = object : TextWatcher {

				// Detects if the user is deleting text
				override fun beforeTextChanged(charSequence: CharSequence?, start: Int, count: Int, after: Int) {
					if (after < count) {
						isDeleting = true
					}
				}

				override fun onTextChanged(charSequence: CharSequence?, start: Int, count: Int, after: Int) {}

				override fun afterTextChanged(text: Editable?) {
					if (!text.isNullOrBlank()) {

						addRecipeViewModel.updateState {
							it.preparationEditable = text
						}

						if (isDeleting) {
							// when deleting, if detects a span, deletes it entirely
							text.getSpans(preparationAdd.selectionStart, preparationAdd.selectionEnd, TagSpan::class.java).also {
								if (it.isNotEmpty()) {
									editText(preparationAdd) {
										text.delete(text.getSpanStart(it[0]), text.getSpanEnd(it[0]))
										text.removeSpan(it[0])
									}
									addRecipeViewModel.updateState { state ->
										state.preparationEditable = text
									}
								}
							}
							isDeleting = false
						}
					}

					if (!text.isNullOrBlank() && preparationAdd.selectionEnd > 0) {
						if (text[preparationAdd.selectionEnd - 1] == "#".toCharArray()[0]) {
							AddSelectRecipeTagFragment(object : DialogListener {
								override fun cancelled() {
									editText(preparationAdd) {
										text.delete(preparationAdd.selectionStart - 1, preparationAdd.selectionStart)
									}
								}

								override fun ready(recipe: Recipe) {
									val name = "#${recipe.name}" // add "#" in span
									val spannableText: Spannable = SpannableString(name)
									spannableText.setSpan(TagSpan(recipe.id.toString()) {
										findNavController().navigate(AddRecipeFragmentDirections.fromAddRecipeFragmentToSingleRecipeFragment(recipe.id))
									}, 0, spannableText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
									preparationAdd.movementMethod = LinkMovementMethod.getInstance()
									editText(preparationAdd) {
										text.replace(
											preparationAdd.selectionStart - 1, preparationAdd.selectionStart, spannableText
										) // replacing the inserted "#"
										text.append(" ")
									}
									addRecipeViewModel.updateState {
										it.preparationEditable = text
									}
								}
							}).show(childFragmentManager, AddSelectRecipeTagFragment.TAG)
						}
					}
				}
			}

			preparationAdd.addTextChangedListener(textWatcherPreparationAdd)
		}

		// ViewModel->UI (Collect the ViewModel StateFlow and with it update the UI)
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
				addRecipeViewModel.state.collect { state ->
					state?.let {
						binding.apply {
							state.name?.let {
								if (nameAdd.text.toString() != state.name) { // it prevents an infinite-loop with the listener
									nameAdd.setText(state.name)
								}
							}
							state.isVeg?.let {
								isVegAdd.isChecked = state.isVeg!!
							}
							state.isCooked?.let {
								isCookedAdd.isChecked = state.isCooked!!
							}
							state.course?.let {
								(courseAdd.editText as AutoCompleteTextView).setText(state.course.toString(), false)
							}
							state.preparationTime?.let {
								(preparationTimeAdd.editText as AutoCompleteTextView).setText(state.preparationTime.toString(), false)
							}
							state.portions?.let {
								if (portionsAdd.text.toString() != state.portions.toString()) {
									portionsAdd.setText(state.portions.toString())
								}
							}
							state.preparationEditable?.let {
								if (preparationAdd.text != state.preparationEditable) {
									preparationAdd.text = state.preparationEditable
								}
							}
							state.ingredientsList?.let {
								// if the UI is different from state
								if (_bindingIngredientsList.size != state.ingredientsList!!.size) {
									// clear layout of all ingredients
									for (i in _bindingIngredientsList.indices) {
										_bindingIngredientsList[i] = null
									}
									// set new values and insert new ingredient
									for (ingredient in state.ingredientsList!!) {
										quantityIngredientAdd.setText(ingredient.quantity)
										nameIngredientAdd.setText(ingredient.name)
										(unitIngredientAdd.editText as AutoCompleteTextView).setText(ingredient.unitOfMeasure.toString(), false)
										addIngredient(false)
									}
								} else {
									for (i in _bindingIngredientsList.indices) {
										_bindingIngredientsList[i]!!.apply {
											val ingredient = state.ingredientsList!![i]
											if (nameIngredientItemAdd.text.toString() != ingredient.name) {
												nameIngredientItemAdd.setText(ingredient.name)
											}
											if (quantityIngredientItemAdd.text.toString() != ingredient.quantity) {
												quantityIngredientItemAdd.setText(ingredient.quantity)
											}
											if (unitIngredientItemAdd.selectedItemPosition != ingredient.unitOfMeasure.ordinal) {
												unitIngredientItemAdd.setSelection(ingredient.unitOfMeasure.ordinal)
											}
										}
									}
								}
							}
						}
					}
				}
			}
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		_binding = null
		for (i in _bindingIngredientsList.indices) {
			_bindingIngredientsList[i] = null
		}
	}

	private fun addIngredient(toInsert: Boolean) {
		binding.apply {
			// Create the binding
			val bindingIngredients = ItemAddIngredientBinding.inflate(layoutInflater)
			_bindingIngredientsList.add(bindingIngredients)
			bindingIngredients.apply {
				// Initialization spinner UnitOfMeasure
				requireContext().let {
					ArrayAdapter(
						it, android.R.layout.simple_spinner_dropdown_item, UnitOfMeasure.values()
					).also { adapter ->
						unitIngredientItemAdd.adapter = adapter
					}
				}

				// Filling UI with new ingredient
				quantityIngredientItemAdd.text = quantityIngredientAdd.text
				unitIngredientItemAdd.setSelection(UnitOfMeasure.of((unitIngredientAdd.editText as AutoCompleteTextView).text.toString())!!.ordinal)
				if (unitIngredientItemAdd.selectedItemPosition == UnitOfMeasure.TO_TASTE.ordinal) {
					quantityIngredientItemAdd.visibility = GONE
				}
				nameIngredientItemAdd.text = nameIngredientAdd.text

				// ERROR HANDLERS
				quantityIngredientItemAdd.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
					if (!hasFocus) AddRecipeCheckErrors.checkQuantityIngredientItem(
						quantityIngredientItemLayoutAdd,
						quantityIngredientItemAdd.text.toString(),
						unitIngredientItemAdd.selectedItem as UnitOfMeasure
					)
				}
				nameIngredientItemAdd.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
					if (!hasFocus) AddRecipeCheckErrors.checkNameIngredientItem(
						nameIngredientItemLayoutAdd, nameIngredientItemAdd.text.toString()
					)
				}

				// Cleanup
				quantityIngredientAdd.text?.clear()
				(unitIngredientAdd.editText as AutoCompleteTextView).setText(UnitOfMeasure.GRAM.toString(), false)
				unitIngredientAdd.visibility = VISIBLE
				nameIngredientAdd.text?.clear()
				nameIngredientLayoutAdd.error = null

				// Add the view to linearLayout
				ingredientsContainerAdd.addView(root)

				if (toInsert) {
					addRecipeViewModel.updateState { state ->
						state.ingredientsList = state.ingredientsList.also { list ->
							list!!.add(
								Ingredient(
									nameIngredientItemAdd.text.toString(),
									quantityIngredientItemAdd.text.toString(),
									unitIngredientItemAdd.selectedItem as UnitOfMeasure
								)
							)
						}
					}
				}

				// Listeners UI->ViewModel
				quantityIngredientItemAdd.doOnTextChanged { text, _, _, _ ->
					addRecipeViewModel.updateState { state ->
						state.ingredientsList = state.ingredientsList.also { list ->
							list!![ingredientsContainerAdd.indexOfChild(root)].quantity = text.toString()
						}
					}
				}

				unitIngredientItemAdd.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
					override fun onItemSelected(
						parent: AdapterView<*>?, view: View, position: Int, id: Long
					) {
						if (position == UnitOfMeasure.TO_TASTE.ordinal) {
							quantityIngredientItemAdd.setText("")
							quantityIngredientItemAdd.visibility = INVISIBLE
						} else {
							quantityIngredientItemAdd.visibility = VISIBLE
						}
						addRecipeViewModel.updateState { state ->
							state.ingredientsList = state.ingredientsList.also { list ->
								list!![ingredientsContainerAdd.indexOfChild(root)].unitOfMeasure = UnitOfMeasure.values()[position]
							}
						}
					}

					override fun onNothingSelected(parent: AdapterView<*>?) {}
				}

				nameIngredientItemAdd.doOnTextChanged { text, _, _, _ ->
					addRecipeViewModel.updateState { state ->
						state.ingredientsList = state.ingredientsList.also { list ->
							list!![ingredientsContainerAdd.indexOfChild(root)].name = text.toString()
						}
					}
				}

				deleteIngredientItemAdd.setOnClickListener {
					addRecipeViewModel.updateState { state ->
						state.ingredientsList = state.ingredientsList.also { list ->
							list!!.removeAt(
								ingredientsContainerAdd.indexOfChild(
									root
								)
							)
						}
					}
					ingredientsContainerAdd.removeView(root)
				}
			}
		}
	}

	@Suppress("DEPRECATION")
	private fun canResolveIntent(intent: Intent): Boolean {
		val packageManager: PackageManager = requireActivity().packageManager
		val resolvedActivity: ResolveInfo? = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
		return resolvedActivity != null
	}

	private fun editText(editText: EditText, edit: () -> Unit) {
		editText.removeTextChangedListener(textWatcherPreparationAdd)
		edit()
		editText.addTextChangedListener(textWatcherPreparationAdd)
	}

	// APPBAR: MENU
	private fun setupMenu() {
		(requireActivity() as MenuHost).addMenuProvider(object : MenuProvider {
			override fun onPrepareMenu(menu: Menu) {
				// Handle for example visibility of menu items
			}

			override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
				menuInflater.inflate(R.menu.fragment_menu_add_recipe, menu)
			}

			override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
				return when (menuItem.itemId) {
					R.id.cancel -> {
						addRecipeViewModel.state.value?.canceled = true
						findNavController().navigateUp()
						true
					}

					else -> false
				}
			}
		}, viewLifecycleOwner, Lifecycle.State.RESUMED)
	}
}