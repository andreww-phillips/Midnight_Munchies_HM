package com.midnightmunchies.domain.ord;

/** The concrete order states. Rejected and Cancelled are terminal and allow no transitions. */
final class OrderStates {

	static final OrderState PLACED = new Placed();
	static final OrderState ACCEPTED = new Accepted();
	static final OrderState PREPARING = new Preparing();
	static final OrderState READY = new Ready();
	static final OrderState REJECTED = new Rejected();
	static final OrderState CANCELLED = new Cancelled();

	private OrderStates() {
	}

	static OrderState of(OrderStatus status) {
		return switch (status) {
			case PLACED -> PLACED;
			case ACCEPTED -> ACCEPTED;
			case PREPARING -> PREPARING;
			case READY -> READY;
			case REJECTED -> REJECTED;
			case CANCELLED -> CANCELLED;
			default -> throw new IllegalArgumentException("Orders cannot be restored with status " + status);
		};
	}

	private static final class Placed extends AbstractOrderState {
		@Override
		public OrderStatus status() {
			return OrderStatus.PLACED;
		}

		@Override
		public OrderState accept() {
			return ACCEPTED;
		}

		@Override
		public OrderState reject() {
			return REJECTED;
		}

		@Override
		public OrderState cancel() {
			return CANCELLED;
		}
	}

	private static final class Accepted extends AbstractOrderState {
		@Override
		public OrderStatus status() {
			return OrderStatus.ACCEPTED;
		}

		@Override
		public OrderState startPreparing() {
			return PREPARING;
		}

		@Override
		public OrderState cancel() {
			return CANCELLED;
		}
	}

	private static final class Preparing extends AbstractOrderState {
		@Override
		public OrderStatus status() {
			return OrderStatus.PREPARING;
		}

		@Override
		public OrderState markReady() {
			return READY;
		}

		@Override
		public OrderState cancel() {
			return CANCELLED;
		}
	}

	private static final class Ready extends AbstractOrderState {
		@Override
		public OrderStatus status() {
			return OrderStatus.READY;
		}
	}

	private static final class Rejected extends AbstractOrderState {
		@Override
		public OrderStatus status() {
			return OrderStatus.REJECTED;
		}
	}

	private static final class Cancelled extends AbstractOrderState {
		@Override
		public OrderStatus status() {
			return OrderStatus.CANCELLED;
		}
	}
}
