package com.npdinfosgroup.api.planner.service;

import com.npdinfosgroup.api.planner.dto.response.ClientResponse;
import com.npdinfosgroup.api.planner.dto.response.ItemsResponse;
import com.npdinfosgroup.api.planner.persistence.repository.ClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the clients for the "Cliente" selectors of the project and resource forms.
 */
@Service
public class ClientQueryService {

	private final ClientRepository clients;

	/**
	 * Creates the service.
	 *
	 * @param clients the clients
	 */
	ClientQueryService(ClientRepository clients) {
		this.clients = clients;
	}

	/**
	 * The clients, by name. The imported "No especificado" client is inactive, so it only appears
	 * among the inactive ones.
	 *
	 * @param active {@code true} for the active clients, {@code false} for the inactive ones
	 * @return the clients
	 */
	@Transactional(readOnly = true)
	public ItemsResponse<ClientResponse> list(boolean active) {
		return new ItemsResponse<>(clients.findByActiveOrderByNameAsc(active)
			.stream()
			.map(client -> new ClientResponse(client.getId(), client.getName(), client.isInternal()))
			.toList());
	}

}
