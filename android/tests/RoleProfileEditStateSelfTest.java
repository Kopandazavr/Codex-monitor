package dev.kopandazavr.codexmonitor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

public final class RoleProfileEditStateSelfTest {
    private RoleProfileEditStateSelfTest(){}
    public static void main(String[] args){
        RoleProfileEditState state=new RoleProfileEditState("role-profile:test",
                Arrays.asList("Planning Review Acceptance","Planning"),
                "Planning Review Acceptance",
                new HashSet<>(Collections.singletonList("Planning Review Acceptance")));
        int before=state.aliases().size();
        require(state.editAlias("Planning Review Acceptance","Planning Review Acceptance",
                Collections.emptyMap()).isEmpty(),"unchanged calendar edit no-op");
        require(state.aliases().size()==before,"no-op creates no copy");
        require(state.editAlias("Planning Review Acceptance","Planner",
                Collections.emptyMap()).isEmpty(),"calendar edit copy");
        require(state.aliases().size()==before+1,"calendar edit adds manual alias");
        require(state.isCalendarAlias("Planning Review Acceptance"),"calendar source preserved");
        require(!state.isCalendarAlias("Planner"),"copy is manual");
        require(state.makePrimary("Planning").isEmpty(),"explicit primary");
        require("Planning".equals(state.primaryAlias()),"primary changed explicitly");
        require(state.markCalendarAlias("Planning"),"manual alias promotes to Calendar");
        require(state.isCalendarAlias("Planning"),"promotion stored");
        System.out.println("Role profile alias semantics PASS");
    }
    private static void require(boolean c,String label){if(!c)throw new AssertionError(label);}
}
