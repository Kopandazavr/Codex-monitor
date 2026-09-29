package dev.kopandazavr.codexmonitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Role/Agent Settings: live status, stable aliases, and durable completed-session history. */
final class RoleSettingsDialog {
    private RoleSettingsDialog() {}

    static void show(Activity activity, CalendarProcess process, Runnable onChanged) {
        if (activity == null || process == null || activity.isFinishing()) return;
        RoleProfileStore.Profile profile = RoleProfileStore.resolve(activity, process.role);
        if (profile == null) return;
        RoleProfileStore.EditSession edit = RoleProfileStore.beginEdit(activity, profile.id);
        if (edit == null) return;
        new Controller(activity, edit,
                new Status(true, process.project, process.topic,
                        process.workStartMillis(), process.beginMillis, 0L),
                onChanged).show();
    }

    static void show(Activity activity, IdleProcessState.IdleRole idle, Runnable onChanged) {
        if (activity == null || idle == null || activity.isFinishing()) return;
        RoleProfileStore.Profile profile = RoleProfileStore.findById(activity, idle.key);
        if (profile == null) profile = RoleProfileStore.resolve(activity, idle.role);
        if (profile == null) return;
        RoleProfileStore.EditSession edit = RoleProfileStore.beginEdit(activity, profile.id);
        if (edit == null) return;
        new Controller(activity, edit,
                new Status(false, idle.project, idle.topic,
                        idle.lastStartedMillis, 0L, idle.lastFinishedMillis),
                onChanged).show();
    }

    private static final class Status {
        final boolean active;
        final String project;
        final String topic;
        final long startedMillis;
        final long deadlineMillis;
        final long finishedMillis;
        Status(boolean active, String project, String topic, long startedMillis,
                long deadlineMillis, long finishedMillis) {
            this.active=active; this.project=clean(project); this.topic=clean(topic);
            this.startedMillis=Math.max(0L,startedMillis);
            this.deadlineMillis=Math.max(0L,deadlineMillis);
            this.finishedMillis=Math.max(0L,finishedMillis);
        }
    }

    private static final class Controller {
        final Activity activity;
        final RoleProfileStore.EditSession edit;
        final Status status;
        final Runnable onChanged;
        final boolean dark;
        final ListView list;
        final LinearLayout content;
        final HistoryAdapter historyAdapter;
        AlertDialog dialog;

        Controller(Activity activity, RoleProfileStore.EditSession edit,
                Status status, Runnable onChanged) {
            this.activity=activity; this.edit=edit; this.status=status; this.onChanged=onChanged;
            this.dark=Ui.isDark(activity);
            this.list=new ListView(activity);
            this.content=new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(Ui.dp(activity,20),Ui.dp(activity,8),
                    Ui.dp(activity,20),0);
            list.setDivider(null);
            list.setDividerHeight(0);
            list.setClipToPadding(false);
            list.setPadding(0,0,0,Ui.dp(activity,16));
            list.addHeaderView(content,null,false);
            this.historyAdapter=new HistoryAdapter();
            list.setAdapter(historyAdapter);
            list.setOnItemClickListener((parent, view, position, id) -> {
                int adapterPosition=position-list.getHeaderViewsCount();
                if(adapterPosition>=0) historyAdapter.toggleDay(adapterPosition);
            });
        }

        void show() {
            render(false);
            dialog=new AlertDialog.Builder(activity).setTitle("Role settings").setView(list)
                    .setNegativeButton("Cancel",null).setPositiveButton("Done",null).create();
            dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> commitAndClose()));
            dialog.show();
        }

        void commitAndClose() {
            String error=edit.commit(activity);
            if(!error.isEmpty()){ Toast.makeText(activity,error,Toast.LENGTH_LONG).show(); return; }
            dialog.dismiss();
            if(onChanged!=null) onChanged.run();
        }

        void render(boolean preserveScroll) {
            int firstPosition=preserveScroll?list.getFirstVisiblePosition():0;
            View firstChild=preserveScroll?list.getChildAt(0):null;
            int firstTop=firstChild==null?0:firstChild.getTop();
            RoleProfileStore.Profile profile=edit.profile();
            content.removeAllViews();
            addStatus(profile);
            addAliases(profile);
            content.addView(sectionTitle("Session history"));
            historyAdapter.setHistory(IdleProcessState.history(activity,profile.id));
            if(preserveScroll) list.post(() -> list.setSelectionFromTop(firstPosition,firstTop));
        }

        void addStatus(RoleProfileStore.Profile profile) {
            content.addView(sectionTitle("Current status"));
            LinearLayout card=Ui.card(activity,dark);
            card.setPadding(Ui.dp(activity,16),Ui.dp(activity,14),
                    Ui.dp(activity,16),Ui.dp(activity,14));
            TextView title=Ui.text(activity,profile.primaryAlias,17f,Ui.mainText(dark));
            title.setTypeface(Ui.mediumTypeface(activity)); card.addView(title);
            long now=System.currentTimeMillis();
            String stateText=status.active
                    ? "Active · "+formatRemaining(status.deadlineMillis-now)+" remaining"
                    : "Idle · "+formatDuration(now-status.finishedMillis);
            TextView state=Ui.text(activity,stateText,12.5f,
                    status.active?Ui.accent(activity,dark):Ui.secondaryText(dark));
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);
            sp.setMargins(0,Ui.dp(activity,4),0,0); card.addView(state,sp);
            if(!status.project.isEmpty()) addMeta(card,status.project,8);
            if(!status.topic.isEmpty()) addMeta(card,status.topic,4);
            if(!status.active && status.startedMillis>0L && status.finishedMillis>status.startedMillis) {
                addMeta(card,"Last session · "+formatDuration(status.finishedMillis-status.startedMillis),5);
            }
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
            cp.setMargins(0,Ui.dp(activity,7),0,Ui.dp(activity,18)); content.addView(card,cp);
        }

        void addMeta(LinearLayout card,String text,int topDp){
            TextView v=Ui.text(activity,text,13f,Ui.secondaryText(dark));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.setMargins(0,Ui.dp(activity,topDp),0,0); card.addView(v,p);
        }

        void addAliases(RoleProfileStore.Profile profile) {
            content.addView(sectionTitle("Known aliases"));
            for(String alias:profile.aliases){
                boolean primary=ProjectProfileRules.normalizeAlias(alias).equals(
                        ProjectProfileRules.normalizeAlias(profile.primaryAlias));
                boolean calendar=edit.isCalendarAlias(alias);
                LinearLayout row=new LinearLayout(activity);
                row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL);
                ImageView provenance=miniIcon(calendar?R.drawable.ic_alias_calendar:R.drawable.ic_project_pencil,
                        calendar?"Calendar alias: copy to edit":"Manual alias: edit");
                provenance.setImageTintList(ColorStateList.valueOf(Ui.secondaryText(dark)));
                provenance.setOnClickListener(v -> editAlias(alias)); row.addView(provenance,
                        new LinearLayout.LayoutParams(Ui.dp(activity,30),Ui.dp(activity,30)));
                TextView aliasText=Ui.text(activity,alias+(primary?" · Primary":""),13.5f,Ui.mainText(dark));
                aliasText.setClickable(true); aliasText.setOnClickListener(v -> editAlias(alias));
                LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(0,-2,1f);
                ap.setMargins(Ui.dp(activity,5),0,0,0); row.addView(aliasText,ap);
                if(!primary){
                    Button make=miniButton("Make Primary");
                    make.setOnClickListener(v -> { String e=edit.makePrimary(alias); if(showError(e)) return; changed(); });
                    row.addView(make,new LinearLayout.LayoutParams(-2,Ui.dp(activity,38)));
                    ImageView del=miniIcon(R.drawable.ic_idle_trash,"Delete alias");
                    del.setImageTintList(ColorStateList.valueOf(Ui.secondaryText(dark)));
                    del.setOnClickListener(v -> { String e=edit.deleteAlias(alias); if(showError(e)) return; changed(); });
                    LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(Ui.dp(activity,32),Ui.dp(activity,32));
                    dp.setMargins(Ui.dp(activity,4),0,0,0); row.addView(del,dp);
                }
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);
                rp.setMargins(0,Ui.dp(activity,6),0,0); content.addView(row,rp);
            }
            Button add=Ui.button(activity,"Add alias",false,dark); add.setTextSize(14);
            add.setOnClickListener(v -> addAlias());
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,Ui.dp(activity,48));
            p.setMargins(0,Ui.dp(activity,10),0,0); content.addView(add,p);
        }

        final class HistoryAdapter extends BaseAdapter {
            private static final int TYPE_DAY = 0;
            private static final int TYPE_SESSION = 1;
            private static final int TYPE_EMPTY = 2;
            private List<IdleProcessState.SessionRecord> history=Collections.emptyList();
            private List<RoleSessionHistory.RowSpec> rows=Collections.emptyList();
            private final Set<Long> expandedDays=new HashSet<>();
            private boolean collapseInitialized;

            void setHistory(List<IdleProcessState.SessionRecord> value) {
                history=value==null?Collections.emptyList():value;
                long[] started=startedMillis();
                long[] finished=finishedMillis();
                if(!collapseInitialized) {
                    long today=RoleSessionHistory.defaultExpandedDay(
                            System.currentTimeMillis(),started,finished);
                    if(today!=Long.MIN_VALUE) expandedDays.add(today);
                    collapseInitialized=true;
                }
                rebuildRows(started,finished);
                notifyDataSetChanged();
            }

            private long[] startedMillis() {
                long[] values=new long[history.size()];
                for(int i=0;i<history.size();i++) values[i]=history.get(i).startedMillis;
                return values;
            }

            private long[] finishedMillis() {
                long[] values=new long[history.size()];
                for(int i=0;i<history.size();i++) values[i]=history.get(i).finishedMillis;
                return values;
            }

            private void rebuildRows(long[] started,long[] finished) {
                rows=RoleSessionHistory.buildRows(started,finished,expandedDays);
            }

            void toggleDay(int position) {
                if(history.isEmpty()||position<0||position>=rows.size()) return;
                RoleSessionHistory.RowSpec row=rows.get(position);
                if(!row.dayHeader) return;
                if(row.expanded) expandedDays.remove(row.dayStartMillis);
                else expandedDays.add(row.dayStartMillis);
                rebuildRows(startedMillis(),finishedMillis());
                notifyDataSetChanged();
            }

            @Override public int getCount(){ return history.isEmpty()?1:rows.size(); }
            @Override public Object getItem(int position){
                return history.isEmpty()?null:rows.get(position);
            }
            @Override public long getItemId(int position){ return position; }
            @Override public boolean isEnabled(int position){
                return !history.isEmpty()&&position>=0&&position<rows.size()
                        &&rows.get(position).dayHeader;
            }
            @Override public int getViewTypeCount(){ return 3; }
            @Override public int getItemViewType(int position){
                if(history.isEmpty()) return TYPE_EMPTY;
                return rows.get(position).dayHeader?TYPE_DAY:TYPE_SESSION;
            }

            @Override
            public View getView(int position,View convertView,ViewGroup parent) {
                int type=getItemViewType(position);
                if(type==TYPE_EMPTY) {
                    TextView empty=convertView instanceof TextView?(TextView)convertView:
                            Ui.text(activity,"",13f,Ui.secondaryText(dark));
                    empty.setText("No completed sessions yet");
                    empty.setTextColor(Ui.secondaryText(dark));
                    empty.setPadding(Ui.dp(activity,20),Ui.dp(activity,8),
                            Ui.dp(activity,20),Ui.dp(activity,16));
                    return empty;
                }
                RoleSessionHistory.RowSpec row=rows.get(position);
                if(type==TYPE_DAY) {
                    LinearLayout day=convertView instanceof LinearLayout
                            ?(LinearLayout)convertView:new LinearLayout(activity);
                    day.removeAllViews();
                    day.setOrientation(LinearLayout.HORIZONTAL);
                    day.setGravity(Gravity.CENTER_VERTICAL);
                    day.setPadding(Ui.dp(activity,20),Ui.dp(activity,12),
                            Ui.dp(activity,20),Ui.dp(activity,4));

                    TextView chevron=Ui.text(activity,row.expanded?"▼":"▶",
                            11.5f,Ui.secondaryText(dark));
                    day.addView(chevron,new LinearLayout.LayoutParams(
                            Ui.dp(activity,20),-2));

                    TextView title=Ui.text(activity,
                            RoleSessionHistory.formatDayHeader(row.dayStartMillis),
                            12.5f,Ui.secondaryText(dark));
                    title.setTypeface(Ui.mediumTypeface(activity));
                    LinearLayout.LayoutParams titleParams=
                            new LinearLayout.LayoutParams(0,-2,1f);
                    titleParams.setMargins(Ui.dp(activity,3),0,Ui.dp(activity,8),0);
                    day.addView(title,titleParams);

                    TextView pill=Ui.text(activity,
                            row.sessionCount+" ("+
                                    RoleSessionHistory.formatDuration(row.totalDurationMillis)+")",
                            11f,Ui.secondaryText(dark));
                    pill.setGravity(Gravity.CENTER);
                    pill.setPadding(Ui.dp(activity,8),Ui.dp(activity,3),
                            Ui.dp(activity,8),Ui.dp(activity,3));
                    GradientDrawable pillBackground=new GradientDrawable();
                    pillBackground.setColor(dark?0xFF303238:0xFFE4E6EA);
                    pillBackground.setCornerRadius(Ui.dp(activity,12));
                    pill.setBackground(pillBackground);
                    day.addView(pill,new LinearLayout.LayoutParams(-2,-2));
                    return day;
                }
                FrameLayout wrapper=convertView instanceof FrameLayout
                        ?(FrameLayout)convertView:new FrameLayout(activity);
                LinearLayout card;
                if(wrapper.getChildCount()==1 && wrapper.getChildAt(0) instanceof LinearLayout) {
                    card=(LinearLayout)wrapper.getChildAt(0);
                    card.removeAllViews();
                } else {
                    wrapper.removeAllViews();
                    card=Ui.card(activity,dark);
                    wrapper.addView(card,new FrameLayout.LayoutParams(-1,-2));
                }
                wrapper.setPadding(Ui.dp(activity,20),Ui.dp(activity,7),
                        Ui.dp(activity,20),0);
                card.setPadding(Ui.dp(activity,16),Ui.dp(activity,12),
                        Ui.dp(activity,16),Ui.dp(activity,12));
                IdleProcessState.SessionRecord record=history.get(row.sessionIndex);
                String task=record.topic.isEmpty()?"Session":record.topic;
                TextView taskView=Ui.text(activity,task,14f,Ui.mainText(dark));
                taskView.setTypeface(Ui.mediumTypeface(activity));
                taskView.setSingleLine(false);
                card.addView(taskView,new LinearLayout.LayoutParams(-1,-2));
                String timing=RoleSessionHistory.formatTiming(
                        record.startedMillis,record.finishedMillis);
                if(!timing.isEmpty()) addMeta(card,timing,6);
                String length=RoleSessionHistory.formatLength(
                        record.startedMillis,record.finishedMillis);
                if(!length.isEmpty()) addMeta(card,length,3);
                return wrapper;
            }
        }

        boolean showError(String error){
            if(error==null||error.isEmpty()) return false;
            Toast.makeText(activity,error,Toast.LENGTH_LONG).show(); return true;
        }

        void editAlias(String alias) {
            EditText input=new EditText(activity); input.setSingleLine(true); input.setText(alias);
            input.setSelection(input.length());
            AlertDialog child=new AlertDialog.Builder(activity).setTitle("Edit role alias")
                    .setView(inputFrame(input)).setNegativeButton("Cancel",null)
                    .setPositiveButton("OK",null).create();
            child.setOnShowListener(x -> child.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String e=edit.editAlias(alias,input.getText()==null?"":input.getText().toString());
                if(!e.isEmpty()){input.setError(e);return;} child.dismiss(); changed();
            })); child.show();
        }

        void addAlias() {
            EditText input=new EditText(activity); input.setSingleLine(true); input.setHint("Role alias");
            AlertDialog child=new AlertDialog.Builder(activity).setTitle("Add role alias")
                    .setView(inputFrame(input)).setNegativeButton("Cancel",null)
                    .setPositiveButton("Add",null).create();
            child.setOnShowListener(x -> child.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String e=edit.addAlias(input.getText()==null?"":input.getText().toString());
                if(!e.isEmpty()){input.setError(e);return;} child.dismiss(); changed();
            })); child.show();
        }

        FrameLayout inputFrame(EditText input){
            FrameLayout frame=new FrameLayout(activity); int pad=Ui.dp(activity,20);
            frame.setPadding(pad,0,pad,0);
            frame.addView(input,new FrameLayout.LayoutParams(-1,Ui.dp(activity,54))); return frame;
        }
        void changed(){render(true);}
        TextView sectionTitle(String text){
            TextView t=Ui.text(activity,text,13f,Ui.secondaryText(dark));
            t.setTypeface(Ui.mediumTypeface(activity)); return t;
        }
        ImageView miniIcon(int drawable,String description){
            ImageView i=new ImageView(activity); i.setImageResource(drawable); i.setContentDescription(description);
            i.setClickable(true); i.setFocusable(true); i.setPadding(Ui.dp(activity,6),Ui.dp(activity,6),
                    Ui.dp(activity,6),Ui.dp(activity,6)); return i;
        }
        Button miniButton(String text){
            Button b=new Button(activity); b.setAllCaps(false); b.setText(text); b.setTextSize(11);
            b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);
            b.setPadding(Ui.dp(activity,8),0,Ui.dp(activity,8),0); return b;
        }
    }

    private static String formatRemaining(long millis){
        long m=Math.max(0L,(Math.max(0L,millis)+59_999L)/60_000L);
        if(m<60L)return m+"m"; long h=m/60L,r=m%60L; return r==0L?h+"h":h+"h "+r+"m";
    }
    private static String formatDuration(long millis){
        long m=Math.max(0L,Math.max(0L,millis)/60_000L);
        if(m<60L)return m+"m"; long h=m/60L,r=m%60L; return r==0L?h+"h":h+"h "+r+"m";
    }
    private static String clean(String v){return v==null?"":v.trim();}
}
