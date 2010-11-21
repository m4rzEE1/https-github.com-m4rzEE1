package com.madgag.agit;

import static android.widget.ExpandableListView.getPackedPositionForChild;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import android.app.ExpandableListActivity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.madgag.agit.DiffSliderView.OnStateUpdateListener;
import com.madgag.agit.LineContextDiffer.Hunk;

public class RevCommitViewer extends ExpandableListActivity {

	private static final String TAG = "RevCommitViewer";
	
	private File gitdir;
	private CommitChangeListAdapter mAdapter;
	private List<FileDiff> fileDiffs;
	private Repository repository;
	
	private DiffSliderView diffSliderView;
	
	private Map<Long, DiffText> diffTexts=new HashMap<Long, DiffText>();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.rev_commit_view);
		diffSliderView=(DiffSliderView) findViewById(R.id.RevCommitDiffSlider);

		Intent intent = getIntent();
		gitdir = RepositoryManagementActivity.getGitDirFrom(intent);

		try {
			
			repository = new FileRepository(gitdir);
			String revisionId = intent.getStringExtra("commit");
			Log.i("RCCV", revisionId);
			RevWalk revWalk = new RevWalk(repository);
			RevCommit commit = revWalk.parseCommit(ObjectId
					.fromString(revisionId));
			Log.i("RCCV", commit.getFullMessage());

			Log.i("RCCV", "Parent count " + commit.getParentCount());
			if (commit.getParentCount() == 1) {
				final TreeWalk tw = new TreeWalk(repository);
				tw.setRecursive(true);
				tw.reset();
				RevCommit commitParent = revWalk.parseCommit(commit
						.getParent(0));
				RevTree commitParentTree = revWalk.parseTree(commitParent
						.getTree());
				tw.addTree(commitParentTree);
				RevTree commitTree = revWalk.parseTree(commit.getTree());
				tw.addTree(commitTree);
				TreeFilter pathFilter = TreeFilter.ANY_DIFF;
				tw.setFilter(pathFilter);
				List<DiffEntry> files = DiffEntry.scan(tw);
				Log.i("RCCV", files.toString());

				boolean detectRenames=true;
//				if (pathFilter instanceof FollowFilter && isAdd(files)) {
					// The file we are following was added here, find where it
					// came from so we can properly show the rename or copy,
					// then continue digging backwards.
					//
					
//					tw.reset();
//					tw.addTree(commitParentTree);
//					tw.addTree(commitTree);
//					tw.setFilter(pathFilter);
//					files = updateFollowFilter(detectRenames(DiffEntry.scan(tw)));
//
//				} else 
				if (detectRenames)
					files = detectRenames(files);

				final LineContextDiffer lineContextDiffer = new LineContextDiffer(repository);
				fileDiffs=Lists.transform(files, new Function<DiffEntry,FileDiff>() {
					public FileDiff apply(DiffEntry d) { return new FileDiff(lineContextDiffer, d); }
				});
				
				mAdapter = new CommitChangeListAdapter(this);
				setListAdapter(mAdapter);

				// ListView listView=(ListView)
				// findViewById(R.id.commit_view_diffs_list);
				// listView.setAdapter(new ArrayAdapter<DiffEntry>(this,
				// android.R.layout.simple_list_item_1, files));
				//				
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private List<DiffEntry> detectRenames(List<DiffEntry> files)
			throws IOException {
		RenameDetector rd = new RenameDetector(repository);
//		if (renameLimit != null)
//			rd.setRenameLimit(renameLimit.intValue());
		rd.addAll(files);
		return rd.compute();
	}

//	private boolean isAdd(List<DiffEntry> files) {
//		String oldPath = ((FollowFilter) pathFilter).getPath();
//		for (DiffEntry ent : files) {
//			if (ent.getChangeType() == ChangeType.ADD
//					&& ent.getNewPath().equals(oldPath))
//				return true;
//		}
//		return false;
//	}
//
//	private List<DiffEntry> updateFollowFilter(List<DiffEntry> files) {
//		String oldPath = ((FollowFilter) pathFilter).getPath();
//		for (DiffEntry ent : files) {
//			if (isRename(ent) && ent.getNewPath().equals(oldPath)) {
//				pathFilter = FollowFilter.create(ent.getOldPath());
//				return Collections.singletonList(ent);
//			}
//		}
//		return Collections.emptyList();
//	}

	private static boolean isRename(DiffEntry ent) {
		return ent.getChangeType() == ChangeType.RENAME
				|| ent.getChangeType() == ChangeType.COPY;
	}

	public class CommitChangeListAdapter extends BaseExpandableListAdapter implements OnStateUpdateListener {

		LayoutInflater mInflater;

		public CommitChangeListAdapter(Context context) {
			mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			diffSliderView.setStateUpdateListener(this);
		}

		public Object getChild(int groupPosition, int childPosition) {
			return fileDiffs.get(groupPosition).getHunks().get(childPosition);
		}

		public long getChildId(int arg0, int arg1) {
			// TODO Auto-generated method stub
			return 0;
		}

		public View getChildView(int groupPosition, int childPosition,
				boolean isLastChild, View convertView, ViewGroup parent) {
			Hunk hunk = fileDiffs.get(groupPosition).getHunks().get(childPosition);
			
			HunkDiffView v = convertView!=null?((HunkDiffView)convertView):new HunkDiffView(RevCommitViewer.this, hunk);
			diffTexts.put(getPackedPositionForChild(groupPosition, childPosition), v.getDiffText());
			return v;
		}

		public int getChildrenCount(int groupPosition) {
			return fileDiffs.get(groupPosition).getHunks().size();
		}

		public Object getGroup(int index) {
			return fileDiffs.get(index);
		}

		public int getGroupCount() {
			return fileDiffs.size();
		}

		public long getGroupId(int index) {
			return fileDiffs.get(index).hashCode(); // Pretty lame
		}

		public View getGroupView(int groupPosition, boolean isExpanded,
				View convertView, ViewGroup parent) {
			View v;
			if (convertView == null) {
				v = newGroupView(isExpanded, parent);
			} else {
				v = convertView;
			}
			DiffEntry diffEntry = fileDiffs.get(groupPosition).getDiffEntry();
			int changeTypeIcon = R.drawable.diff_changetype_modify;
			String filename = diffEntry.getNewPath();
			switch (diffEntry.getChangeType()) {
			case ADD:
				changeTypeIcon = R.drawable.diff_changetype_add;
				break;
			case DELETE:
				changeTypeIcon = R.drawable.diff_changetype_delete;
				filename = diffEntry.getOldPath();
				break;
			case MODIFY:
				changeTypeIcon = R.drawable.diff_changetype_modify;
				break;
			case RENAME:
				changeTypeIcon = R.drawable.diff_changetype_rename;
				filename = nameChange(diffEntry);
				break;
			case COPY:
				changeTypeIcon = R.drawable.diff_changetype_add;
				break;
			}
			((ImageView) v.findViewById(R.id.commit_file_diff_type))
					.setImageResource(changeTypeIcon);
			((TextView) v.findViewById(R.id.commit_file_textview))
					.setText(filename);

			return v;
		}

		private String nameChange(DiffEntry diffEntry) {
			return new FilePathDiffer().diff(diffEntry.getOldPath(), diffEntry.getNewPath());
		}

		private View newGroupView(boolean isExpanded, ViewGroup parent) {
			return mInflater.inflate(isExpanded ? R.layout.commit_group_view
					: R.layout.commit_group_view, parent, false);
		}

		public boolean hasStableIds() {
			// TODO Auto-generated method stub
			return false;
		}

		public boolean isChildSelectable(int arg0, int arg1) {
			// TODO Auto-generated method stub
			return false;
		}

		public void onStateChanged(DiffSliderView diffSliderView, float state) {
			ExpandableListView expandableListView = getExpandableListView();
			for (int i=0;i<fileDiffs.size();++i) {
				if (expandableListView.isGroupExpanded(i)) {
					for (int j=0;j<getChildrenCount(i);++j) {
						DiffText diffText = diffTexts.get(getPackedPositionForChild(i, j));
						if (diffText!=null) {
							diffText.setTransitionProgress(state);
						}
					}
				}
			}
		}

	}
}
