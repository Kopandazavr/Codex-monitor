package dev.kopandazavr.codexmonitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
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
import java.util.List;

/** Role/Agent Settings: live status, aliases, and durable virtualized session history. */
final class RoleSettingsDialog {
    private RoleSettingsDialog() {}

    static void show(Activity a, CalendarProcess p, Runnable changed) {
        if (a==null||p==null||a.isFinishing()) return;
        RoleProfileStore.Profile profile=RoleProfileStore.resolve(a,p.role);
        if(profile==null)return;
        RoleProfileStore.EditSession edit=RoleProfileStore.beginEdit(a,profile.id);
        if(edit!=null)new Controller(a,edit,new Status(true,p.project,p.topic,
                p.workStartMillis(),p.beginMillis,0L),changed).show();
    }

    static void show(Activity a, IdleProcessState.IdleRole idle, Runnable changed) {
        if(a==null||idle==null||a.isFinishing())return;
        RoleProfileStore.Profile profile=RoleProfileStore.findById(a,idle.key);
        if(profile==null)profile=RoleProfileStore.resolve(a,idle.role);
        if(profile==null)return;
        RoleProfileStore.EditSession edit=RoleProfileStore.beginEdit(a,profile.id);
        if(edit!=null)new Controller(a,edit,new Status(false,idle.project,idle.topic,
                idle.lastStartedMillis,0L,idle.lastFinishedMillis),changed).show();
    }

    private static final class Status {
        final boolean active; final String project,topic;
        final long startedMillis,deadlineMillis,finishedMillis;
        Status(boolean active,String project,String topic,long started,long deadline,long finished){
            this.active=active;this.project=clean(project);this.topic=clean(topic);
            startedMillis=Math.max(0L,started);deadlineMillis=Math.max(0L,deadline);
            finishedMillis=Math.max(0L,finished);
        }
    }

    private static final class Controller {
        final Activity a; final RoleProfileStore.EditSession edit; final Status status;
        final Runnable onChanged; final boolean dark; final ListView list;
        final LinearLayout header; final HistoryAdapter historyAdapter;
        AlertDialog dialog;

        Controller(Activity a,RoleProfileStore.EditSession edit,Status status,Runnable changed){
            this.a=a;this.edit=edit;this.status=status;onChanged=changed;dark=Ui.isDark(a);
            list=new ListView(a);list.setDivider(null);list.setDividerHeight(0);
            list.setClipToPadding(false);list.setPadding(0,0,0,Ui.dp(a,16));
            header=new LinearLayout(a);header.setOrientation(LinearLayout.VERTICAL);
            header.setPadding(Ui.dp(a,20),Ui.dp(a,8),Ui.dp(a,20),0);
            list.addHeaderView(header,null,false);
            historyAdapter=new HistoryAdapter();list.setAdapter(historyAdapter);
        }

        void show(){
            render(false);
            dialog=new AlertDialog.Builder(a).setTitle("Role settings").setView(list)
                    .setNegativeButton("Cancel",null).setPositiveButton("Done",null).create();
            dialog.setOnShowListener(x->dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v->commitAndClose()));
            dialog.show();
        }

        void commitAndClose(){
            String e=edit.commit(a);
            if(!e.isEmpty()){Toast.makeText(a,e,Toast.LENGTH_LONG).show();return;}
            dialog.dismiss();if(onChanged!=null)onChanged.run();
        }

        void render(boolean preserve){
            int pos=preserve?list.getFirstVisiblePosition():0;
            View child=preserve?list.getChildAt(0):null; int top=child==null?0:child.getTop();
            RoleProfileStore.Profile p=edit.profile();
            header.removeAllViews();addStatus(p);addAliases(p);header.addView(section("Session history"));
            historyAdapter.setHistory(IdleProcessState.history(a,p.id));
            if(preserve)list.post(()->list.setSelectionFromTop(pos,top));
        }

        void addStatus(RoleProfileStore.Profile p){
            header.addView(section("Current status"));
            LinearLayout card=Ui.card(a,dark);card.setPadding(Ui.dp(a,16),Ui.dp(a,14),Ui.dp(a,16),Ui.dp(a,14));
            TextView title=Ui.text(a,p.primaryAlias,17f,Ui.mainText(dark));
            title.setTypeface(Ui.mediumTypeface(a));card.addView(title);
            long now=System.currentTimeMillis();
            String state=status.active?"Active · "+formatRemaining(status.deadlineMillis-now)+" remaining"
                    :"Idle · "+formatDuration(now-status.finishedMillis);
            TextView sv=Ui.text(a,state,12.5f,status.active?Ui.accent(a,dark):Ui.secondaryText(dark));
            LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);
            sp.setMargins(0,Ui.dp(a,4),0,0);card.addView(sv,sp);
            if(!status.project.isEmpty())addMeta(card,status.project,8);
            if(!status.topic.isEmpty())addMeta(card,status.topic,4);
            if(!status.active&&status.startedMillis>0L&&status.finishedMillis>status.startedMillis)
                addMeta(card,"Last session · "+formatDuration(status.finishedMillis-status.startedMillis),5);
            LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
            cp.setMargins(0,Ui.dp(a,7),0,Ui.dp(a,18));header.addView(card,cp);
        }

        void addMeta(LinearLayout card,String text,int top){
            TextView v=Ui.text(a,text,13f,Ui.secondaryText(dark));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
            p.setMargins(0,Ui.dp(a,top),0,0);card.addView(v,p);
        }

        void addAliases(RoleProfileStore.Profile p){
            header.addView(section("Known aliases"));
            for(String alias:p.aliases){
                boolean primary=ProjectProfileRules.normalizeAlias(alias).equals(
                        ProjectProfileRules.normalizeAlias(p.primaryAlias));
                boolean calendar=edit.isCalendarAlias(alias);
                LinearLayout row=new LinearLayout(a);row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                ImageView provenance=icon(calendar?R.drawable.ic_alias_calendar:R.drawable.ic_project_pencil,
                        calendar?"Calendar alias: copy to edit":"Manual alias: edit");
                provenance.setImageTintList(ColorStateList.valueOf(Ui.secondaryText(dark)));
                provenance.setOnClickListener(v->editAlias(alias));
                row.addView(provenance,new LinearLayout.LayoutParams(Ui.dp(a,30),Ui.dp(a,30)));
                TextView text=Ui.text(a,alias+(primary?" · Primary":""),13.5f,Ui.mainText(dark));
                text.setClickable(true);text.setOnClickListener(v->editAlias(alias));
                LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1f);
                tp.setMargins(Ui.dp(a,5),0,0,0);row.addView(text,tp);
                if(!primary){
                    Button make=miniButton("Make Primary");
                    make.setOnClickListener(v->{String e=edit.makePrimary(alias);if(!showError(e))changed();});
                    row.addView(make,new LinearLayout.LayoutParams(-2,Ui.dp(a,38)));
                    ImageView del=icon(R.drawable.ic_idle_trash,"Delete alias");
                    del.setImageTintList(ColorStateList.valueOf(Ui.secondaryText(dark)));
                    del.setOnClickListener(v->{String e=edit.deleteAlias(alias);if(!showError(e))changed();});
                    LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(Ui.dp(a,32),Ui.dp(a,32));
                    dp.setMargins(Ui.dp(a,4),0,0,0);row.addView(del,dp);
                }
                LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);
                rp.setMargins(0,Ui.dp(a,6),0,0);header.addView(row,rp);
            }
            Button add=Ui.button(a,"Add alias",false,dark);add.setTextSize(14);add.setOnClickListener(v->addAlias());
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,Ui.dp(a,48));
            ap.setMargins(0,Ui.dp(a,10),0,0);header.addView(add,ap);
        }

        final class HistoryAdapter extends BaseAdapter {
            private static final int DAY=0,SESSION=1,EMPTY=2;
            private List<IdleProcessState.SessionRecord> history=Collections.emptyList();
            private List<RoleSessionHistory.RowSpec> rows=Collections.emptyList();

            void setHistory(List<IdleProcessState.SessionRecord> value){
                history=value==null?Collections.emptyList():value;
                long[] finished=new long[history.size()];
                for(int i=0;i<history.size();i++)finished[i]=history.get(i).finishedMillis;
                rows=RoleSessionHistory.buildRows(finished);notifyDataSetChanged();
            }
            @Override public int getCount(){return history.isEmpty()?1:rows.size();}
            @Override public Object getItem(int p){return history.isEmpty()?null:rows.get(p);}
            @Override public long getItemId(int p){return p;}
            @Override public boolean isEnabled(int p){return false;}
            @Override public int getViewTypeCount(){return 3;}
            @Override public int getItemViewType(int p){
                if(history.isEmpty())return EMPTY;return rows.get(p).dayHeader?DAY:SESSION;
            }
            @Override public View getView(int p,View convert,ViewGroup parent){
                int type=getItemViewType(p);
                if(type==EMPTY){
                    TextView v=convert instanceof TextView?(TextView)convert:Ui.text(a,"",13f,Ui.secondaryText(dark));
                    v.setText("No completed sessions yet");v.setTextColor(Ui.secondaryText(dark));
                    v.setPadding(Ui.dp(a,20),Ui.dp(a,8),Ui.dp(a,20),Ui.dp(a,16));return v;
                }
                RoleSessionHistory.RowSpec row=rows.get(p);
                if(type==DAY){
                    TextView v=convert instanceof TextView?(TextView)convert:Ui.text(a,"",12.5f,Ui.secondaryText(dark));
                    v.setTypeface(Ui.mediumTypeface(a));v.setTextColor(Ui.secondaryText(dark));
                    v.setText(RoleSessionHistory.formatDayHeader(row.dayStartMillis));
                    v.setPadding(Ui.dp(a,20),Ui.dp(a,12),Ui.dp(a,20),Ui.dp(a,2));return v;
                }
                FrameLayout wrap=convert instanceof FrameLayout?(FrameLayout)convert:new FrameLayout(a);
                LinearLayout card;
                if(wrap.getChildCount()==1&&wrap.getChildAt(0) instanceof LinearLayout){
                    card=(LinearLayout)wrap.getChildAt(0);card.removeAllViews();
                }else{wrap.removeAllViews();card=Ui.card(a,dark);wrap.addView(card,new FrameLayout.LayoutParams(-1,-2));}
                wrap.setPadding(Ui.dp(a,20),Ui.dp(a,7),Ui.dp(a,20),0);
                card.setPadding(Ui.dp(a,16),Ui.dp(a,12),Ui.dp(a,16),Ui.dp(a,12));
                IdleProcessState.SessionRecord record=history.get(row.sessionIndex);
                TextView task=Ui.text(a,record.topic.isEmpty()?"Session":record.topic,14f,Ui.mainText(dark));
                task.setTypeface(Ui.mediumTypeface(a));task.setSingleLine(false);
                card.addView(task,new LinearLayout.LayoutParams(-1,-2));
                String timing=RoleSessionHistory.formatTiming(record.startedMillis,record.finishedMillis);
                if(!timing.isEmpty())addMeta(card,timing,6);
                String length=RoleSessionHistory.formatLength(record.startedMillis,record.finishedMillis);
                if(!length.isEmpty())addMeta(card,length,3);
                return wrap;
            }
        }

        boolean showError(String e){if(e==null||e.isEmpty())return false;Toast.makeText(a,e,Toast.LENGTH_LONG).show();return true;}
        void editAlias(String alias){
            EditText input=new EditText(a);input.setSingleLine(true);input.setText(alias);input.setSelection(input.length());
            AlertDialog d=new AlertDialog.Builder(a).setTitle("Edit role alias").setView(inputFrame(input))
                    .setNegativeButton("Cancel",null).setPositiveButton("OK",null).create();
            d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String e=edit.editAlias(alias,input.getText()==null?"":input.getText().toString());
                if(!e.isEmpty()){input.setError(e);return;}d.dismiss();changed();}));d.show();
        }
        void addAlias(){
            EditText input=new EditText(a);input.setSingleLine(true);input.setHint("Role alias");
            AlertDialog d=new AlertDialog.Builder(a).setTitle("Add role alias").setView(inputFrame(input))
                    .setNegativeButton("Cancel",null).setPositiveButton("Add",null).create();
            d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                String e=edit.addAlias(input.getText()==null?"":input.getText().toString());
                if(!e.isEmpty()){input.setError(e);return;}d.dismiss();changed();}));d.show();
        }
        FrameLayout inputFrame(EditText input){
            FrameLayout f=new FrameLayout(a);int pad=Ui.dp(a,20);f.setPadding(pad,0,pad,0);
            f.addView(input,new FrameLayout.LayoutParams(-1,Ui.dp(a,54)));return f;
        }
        void changed(){render(true);}
        TextView section(String text){TextView t=Ui.text(a,text,13f,Ui.secondaryText(dark));t.setTypeface(Ui.mediumTypeface(a));return t;}
        ImageView icon(int drawable,String description){
            ImageView i=new ImageView(a);i.setImageResource(drawable);i.setContentDescription(description);
            i.setClickable(true);i.setFocusable(true);i.setPadding(Ui.dp(a,6),Ui.dp(a,6),Ui.dp(a,6),Ui.dp(a,6));return i;
        }
        Button miniButton(String text){
            Button b=new Button(a);b.setAllCaps(false);b.setText(text);b.setTextSize(11);
            b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);
            b.setPadding(Ui.dp(a,8),0,Ui.dp(a,8),0);return b;
        }
    }

    private static String formatRemaining(long ms){
        long m=Math.max(0L,(Math.max(0L,ms)+59_999L)/60_000L);
        if(m<60L)return m+"m";long h=m/60L,r=m%60L;return r==0L?h+"h":h+"h "+r+"m";
    }
    private static String formatDuration(long ms){
        long m=Math.max(0L,Math.max(0L,ms)/60_000L);
        if(m<60L)return m+"m";long h=m/60L,r=m%60L;return r==0L?h+"h":h+"h "+r+"m";
    }
    private static String clean(String v){return v==null?"":v.trim();}
}
