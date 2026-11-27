import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import {
  type ExperimentStatus,
  getExperiment,
  startExperiment,
  stopExperiment,
} from '@/features/review/api/experimentApi';

/** Chu kỳ hỏi lại tiến độ khi thực nghiệm đang chạy. Một ô mất vài giây tới vài chục giây. */
export const EXPERIMENT_POLL_MS = 5000;

export const experimentKeys = {
  status: (runId: string) => ['experiment', runId] as const,
};

/**
 * Tiến độ + chỉ số. Chỉ hỏi lại định kỳ khi máy chủ báo đang chạy: thực nghiệm kéo dài nhiều giờ,
 * còn lúc đứng yên thì poll chỉ tốn request.
 */
export function useExperiment(runId: string) {
  return useQuery<ExperimentStatus>({
    queryKey: experimentKeys.status(runId),
    queryFn: () => getExperiment(runId),
    enabled: runId !== '',
    refetchInterval: (query) => (query.state.data?.running === true ? EXPERIMENT_POLL_MS : false),
  });
}

function useExperimentAction(runId: string, action: (runId: string) => Promise<ExperimentStatus>) {
  const queryClient = useQueryClient();
  return useMutation<ExperimentStatus, Error>({
    mutationFn: () => action(runId),
    // Đặt thẳng trạng thái máy chủ trả về: `running` đổi ngay, nên poll bật/tắt đúng lúc.
    onSuccess: (status) => queryClient.setQueryData(experimentKeys.status(runId), status),
  });
}

export function useStartExperiment(runId: string) {
  return useExperimentAction(runId, startExperiment);
}

export function useStopExperiment(runId: string) {
  return useExperimentAction(runId, stopExperiment);
}
