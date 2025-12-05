package com.example.scheduler.loadexecutor.planner;

import com.example.scheduler.loadexecutor.domain.Command;
import com.example.scheduler.loadexecutor.domain.LoadPlan;

public interface LoadPlanner {

    LoadPlan plan(Command command);
}
