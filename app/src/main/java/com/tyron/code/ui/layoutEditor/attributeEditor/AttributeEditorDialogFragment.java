package com.tyron.code.ui.layoutEditor.attributeEditor;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tyron.code.ApplicationLoader;
import com.tyron.code.R;

import java.util.ArrayList;
import java.util.List;

import kotlin.Pair;

public class AttributeEditorDialogFragment extends BottomSheetDialogFragment {

    public static final String KEY_ATTRIBUTE_CHANGED = "ATTRIBUTE_CHANGED";
    public static final String KEY_ATTRIBUTE_REMOVED = "ATTRIBUTE_REMOVED";

    public static AttributeEditorDialogFragment newInstance(ArrayList<Pair<String, String>> availableAttributes, ArrayList<Pair<String, String>> attributes) {
        Bundle args = new Bundle();
        args.putSerializable("attributes", attributes);
        args.putSerializable("availableAttributes", availableAttributes);
        AttributeEditorDialogFragment fragment = new AttributeEditorDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private AttributeEditorAdapter mAdapter;
    private ArrayList<Pair<String, String>> mAvailableAttributes;
    private ArrayList<Pair<String, String>> mAttributes;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //noinspection unchecked
        mAttributes = (ArrayList<Pair<String, String>>) requireArguments().getSerializable(
                "attributes");
        if (mAttributes == null) {
            mAttributes = new ArrayList<>();
        }

        //noinspection unchecked
        mAvailableAttributes =
                (ArrayList<Pair<String, String>>) requireArguments().getSerializable(
                        "availableAttributes");
        if (mAvailableAttributes == null) {
            mAvailableAttributes = new ArrayList<>();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.attribute_editor_dialog_fragment, container, false);

        mAdapter = new AttributeEditorAdapter();
        RecyclerView recyclerView = root.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(mAdapter);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if(getDialog() != null){
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        mAdapter.setItemClickListener((pos, attribute) -> {
            View v = LayoutInflater.from(requireContext()).inflate(R.layout.attribute_editor_input, null);
            EditText editText = v.findViewById(R.id.value);
            editText.setText(attribute.getSecond());
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(attribute.getFirst())
                    .setView(v).setPositiveButton("apply", (d, w) -> {
                List<Pair<String, String>> attributes = mAdapter.getAttributes();
                attributes.set(pos, new Pair<>(attribute.getFirst(),
                        editText.getText().toString()));
                mAdapter.submitList(attributes);

                Bundle bundle = new Bundle();
                bundle.putString("key", attribute.getFirst());
                bundle.putString("value", editText.getText().toString());
                getParentFragmentManager().setFragmentResult(KEY_ATTRIBUTE_CHANGED, bundle);
            }).show();
        });
        mAdapter.submitList(mAttributes);

        LinearLayout linearAdd = view.findViewById(R.id.linear_add);
        linearAdd.setOnClickListener(v -> {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
            builder.setTitle("Available Attributes");

            final ArrayList<CharSequence> items = new ArrayList<>();
            final ArrayList<Pair<String, String>> filteredAttributes = new ArrayList<>();

            Loop1: for (int i = 0; i < mAvailableAttributes.size(); i++) {
                for(Pair<String, String> pair : mAttributes){
                    if(pair.getFirst().equals(mAvailableAttributes.get(i).getFirst()))
                        continue Loop1;
                }
                filteredAttributes.add(mAvailableAttributes.get(i));
                items.add(mAvailableAttributes.get(i).getFirst());
            }
            boolean[] selectedAttrs = new boolean[filteredAttributes.size()];
            builder.setMultiChoiceItems(items.toArray(new CharSequence[0]), selectedAttrs, (d, which, isSelected) -> {
               selectedAttrs[which] = isSelected;
            });
            builder.setPositiveButton("Add", ((dialogInterface, which) -> {
                for(int i = 0; i < selectedAttrs.length; i++){
                    if(selectedAttrs[i]){
                        mAttributes.add(filteredAttributes.get(i));
                    }
                }
                mAdapter.submitList(mAttributes);
            }));
            builder.show();
        });

        getParentFragmentManager().setFragmentResultListener(KEY_ATTRIBUTE_REMOVED, getViewLifecycleOwner(), ((requestKey, result) -> {
            String key = result.getString("key");
            int index = -1;
            for (Pair<String, String> pair : mAttributes) {
                if (pair.getFirst().equals(key)) {
                    index = mAttributes.indexOf(pair);
                }
            }
            if (index != -1) {
                mAttributes.remove(index);
                mAdapter.submitList(mAttributes);
            }
        }));
    }
}
